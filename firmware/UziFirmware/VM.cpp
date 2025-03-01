#include "VM.h"

uint8 loop_count = 0;

Error VM::executeProgram(Program* program, GPIO* io, Monitor* monitor)
{
	if (program != currentProgram)
	{
		currentProgram = program;
		currentCoroutine = 0;
	}

	int16 count = program->getScriptCount();

	lastTickStart = millis();
	loop_count = 0;
	for (int16 i = 0; i < count; i++)
	{
		Script* script = program->getScript(i);
		if (script->isRunning())
		{
			Coroutine* coroutine = script->getCoroutine();
			if (coroutine == 0)
			{
				return OUT_OF_MEMORY;
			}
			if (lastTickStart >= coroutine->getNextRun())
			{
				executeCoroutine(coroutine, io, monitor);
			}
		}
	}
	return NO_ERROR;
}

void VM::executeCoroutine(Coroutine* coroutine, GPIO* io, Monitor* monitor)
{
	if (this->halted
		|| (this->haltedScript != NULL && this->haltedScript != currentProgram->getScript(coroutine->scriptIndex)))
	{
		/*
		INFO(Richo): Even though we won't execute this coroutine on this tick, I still
		adjust the last start so that when the VM continues the tasks are all in sync.
		*/
		coroutine->setLastStart(lastTickStart);
		return;
	}
	if (currentCoroutine != coroutine)
	{
		if (currentCoroutine != 0)
		{
			saveCurrentCoroutine();
		}
		currentCoroutine = coroutine;
		error = coroutine->restoreStack();
		pc = coroutine->getPC();
		currentScript = currentProgram->getScript(coroutine->activeScriptIndex);
		framePointer = coroutine->getFramePointer();
	}

	if (framePointer == -1)
	{
		framePointer = stack_getPointer();
		for (int i = 0; i < currentScript->getArgCount(); i++)
		{
			stack_push(0, error);
		}
		for (int i = 0; i < currentScript->getLocalCount(); i++)
		{
			stack_push(currentProgram->getGlobal(currentScript->getLocal(i)), error);
		}
		stack_push(uint32_to_float((uint32)-1 << 16 | pc), error);

		coroutine->setLastStart(lastTickStart);
	}
	bool yieldFlag = false;
	while (true)
	{
		if (pc <= currentScript->getInstructionStop())
		{
			Instruction next = currentProgram->getInstructionAt(pc);
			if (getBreakpoint(&next) && this->haltedScript == NULL)
			{
				this->halted = true;
				this->haltedScript = currentProgram->getScript(coroutine->scriptIndex);
				//this call is to ensure that the monitor has access to the updated state of the coroutine in the case of a halt.
				saveCurrentCoroutine();
				coroutine->setNextRun(lastTickStart);
				break;
			}
			this->haltedScript = NULL;
			pc++;
			executeInstruction(next, io, monitor, yieldFlag);
		}
		if (coroutine->hasError()) break;
		if (error != NO_ERROR)
		{
			currentProgram->setCoroutineError(coroutine, error);
			break;
		}
		if (yieldFlag)
		{
			break;
		}
		if (pc > currentScript->getInstructionStop())
		{
			if (currentScript->type == TASK) 
			{
				currentScript->setRunning(false);
			}

			bool returnFromScriptCall = framePointer != 0;
			unwindStackAndReturn();

			if (!returnFromScriptCall)
			{
				/*
				INFO(Richo):
				If we get here it means we weren't called from other script, we just reached
				the end of the script after a regular tick. We don't have to return any value.
				We simply reset the coroutine state and break out of the loop.
				*/

				framePointer = -1;
				pc = currentScript->getInstructionStart();
				int32 nextRun = coroutine->getLastStart() + (int32)currentProgram->getGlobal(currentScript->interval);
				coroutine->setNextRun(nextRun);
				break;
			}
		}
	}
}

void VM::saveCurrentCoroutine()
{
	Error error = currentCoroutine->saveStack();
	if (error != NO_ERROR)
	{
		currentProgram->setCoroutineError(currentCoroutine, error);
	}
	currentCoroutine->activeScriptIndex = currentScript->index;
	currentCoroutine->setFramePointer(framePointer);
	currentCoroutine->setPC(pc);
}

void VM::executeInstruction(Instruction instruction, GPIO* io, Monitor* monitor, bool& yieldFlag)
{
	Opcode opcode = (Opcode)instruction.opcode;
	int16 argument = getArgument(&instruction);

	switch (opcode)
	{
	case TURN_ON:
	{
		io->setValue((uint8)argument, HIGH);
	}
	break;

	case TURN_OFF:
	{
		io->setValue((uint8)argument, LOW);
	}
	break;

	case WRITE_PIN:
	{
		io->setValue((uint8)argument, stack_pop(error));
	}
	break;

	case READ_PIN:
	{
		stack_push(io->getValue((uint8)argument), error);
	}
	break;

	case READ_GLOBAL:
	{
		stack_push(currentProgram->getGlobal(argument), error);
	}
	break;

	case WRITE_GLOBAL:
	{
		currentProgram->setGlobal(argument, stack_pop(error));
	}
	break;

	case SCRIPT_CALL:
	{
		/*
		INFO(Richo):
		We know the arguments are already on the stack (it's the compiler's job
		to push them). Now we need to push:
			1) The local variables with their default values.
			2) The current framePointer and returnAddress (so that when unwinding
			the stack, they can be set correctly).
		*/
		currentScript = currentProgram->getScript(argument);
		int16 fp = stack_getPointer() - currentScript->getArgCount();
		for (int i = 0; i < currentScript->getLocalCount(); i++)
		{
			stack_push(currentProgram->getGlobal(currentScript->getLocal(i)), error);
		}
		stack_push(uint32_to_float((uint32)framePointer << 16 | (pc - 1)), error);

		/*
		INFO(Richo):
		After the stack is configured. We set the framePointer and pc to their
		new values and continue execution.
		*/
		framePointer = fp;
		pc = currentScript->getInstructionStart();
	}
	break;

	case SCRIPT_START:
	{
		Script* script = currentProgram->getScript(argument);
		if (script != 0)
		{
			script->setRunning(true);

			Coroutine* coroutine = script->getCoroutine();
			if (coroutine == 0)
			{
				currentProgram->setCoroutineError(currentCoroutine, OUT_OF_MEMORY);
			}
			else if (currentCoroutine == coroutine)
			{
				/*
				If we're starting the current coroutine we need to restart execution
				right now. So, we set the yield flag and reset the vm state.
				*/
				yieldFlag = true;
				stack_reset();
				error = NO_ERROR;
				pc = script->getInstructionStart();
				framePointer = -1;
			}
			else
			{
				/*
				If we're starting another coroutine just resetting the coroutine
				state is enough.
				*/
				currentProgram->resetCoroutine(coroutine);
			}
		}
	}
	break;

	case SCRIPT_RESUME:
	{
		Script* script = currentProgram->getScript(argument);
		if (script != 0)
		{
			script->setRunning(true);
		}
	}
	break;

	case SCRIPT_STOP:
	{
		Script* script = currentProgram->getScript(argument);
		if (script != 0)
		{
			script->setRunning(false);

			Coroutine* coroutine = script->getCoroutine();
			if (coroutine == 0)
			{
				currentProgram->setCoroutineError(currentCoroutine, OUT_OF_MEMORY);
			}
			else if (currentCoroutine == coroutine)
			{
				/*
				If we're stopping the current coroutine we need to stop execution
				right now. So, we set the yield flag and reset the vm state.
				*/
				yieldFlag = true;
				stack_reset();
				error = NO_ERROR;
				pc = script->getInstructionStart();
				framePointer = -1;
			}
			else
			{
				/*
				If we're stopping another coroutine just resetting the coroutine
				state is enough.
				*/
				currentProgram->resetCoroutine(coroutine);
			}
		}
	}
	break;

	case SCRIPT_PAUSE:
	{
		Script* script = currentProgram->getScript(argument);
		if (script != 0)
		{
			script->setRunning(false);

			/*
			If we're stopping the current coroutine we need to stop execution
			right now. But we don't need to reset the coroutine because we will
			resume execution from this point.
			*/
			Coroutine* coroutine = script->getCoroutine();
			if (coroutine == 0)
			{
				currentProgram->setCoroutineError(currentCoroutine, OUT_OF_MEMORY);
			}
			else if (currentCoroutine == coroutine)
			{
				yieldFlag = true;
			}
		}
	}
	break;


	case JMP:
	{
		pc += argument; 
		handleBackwardJump(argument, yieldFlag);
	}
	break;

	case JZ:
	{
		if (stack_pop(error) == 0) // TODO(Richo): Float comparison
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;

	case JNZ:
	{
		if (stack_pop(error) != 0) // TODO(Richo): Float comparison
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;

	/*
	case JNE:
	{
		float a = stack_pop(error);
		float b = stack_pop(error);
		if (a != b) // TODO(Richo): float comparison
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;
	*/

	/*
	case JLT:
	{
		float b = stack_pop(error);
		float a = stack_pop(error);
		if (a < b)
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;
	*/

	case JLTE:
	{
		float b = stack_pop(error);
		float a = stack_pop(error);
		if (a <= b)
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;

	/*
	case JGT:
	{
		float b = stack_pop(error);
		float a = stack_pop(error);
		if (a > b)
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;
	*/

	/*
	case JGTE:
	{
		float b = stack_pop(error);
		float a = stack_pop(error);
		if (a >= b)
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;
	*/

	case PRIM_JMP:
	{
		int16 argument = (int16)stack_pop(error);
		pc += argument;
		handleBackwardJump(argument, yieldFlag);
	}
	break;

	case PRIM_JZ:
	{
		int16 argument = (int16)stack_pop(error);
		float value = stack_pop(error);
		if (value == 0) // TODO(Richo): Float comparison
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;

	case PRIM_JNZ:
	{
		int16 argument = (int16)stack_pop(error);
		float value = stack_pop(error);
		if (value != 0) // TODO(Richo): Float comparison
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;

	case PRIM_JLTE:
	{
		int16 argument = (int16)stack_pop(error);
		float b = stack_pop(error);
		float a = stack_pop(error);
		if (a <= b)
		{
			pc += argument;
			handleBackwardJump(argument, yieldFlag);
		}
	}
	break;

	case READ_LOCAL:
	{
		uint16 index = framePointer + argument;
		float value = stack_getElementAt(index, error);
		stack_push(value, error);
	}
	break;

	case WRITE_LOCAL:
	{
		uint16 index = framePointer + argument;
		float value = stack_pop(error);
		stack_setElementAt(index, value, error);
	}
	break;

	case PRIM_READ_PIN:
	{
		uint8 pin = (uint8)stack_pop(error);
		stack_push(io->getValue(pin), error);
	}
	break;

	case PRIM_WRITE_PIN:
	{
		float value = stack_pop(error);
		uint8 pin = (uint8)stack_pop(error);
		io->setValue(pin, value);
	}
	break;

	case PRIM_SERVO_WRITE:
	{
		float value = stack_pop(error);
		uint8 pin = (uint8)stack_pop(error);
		io->servoWrite(pin, value);
	}
	break;

	case PRIM_MULTIPLY:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 * val2, error);
	}
	break;

	case PRIM_ADD:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 + val2, error);
	}
	break;

	case PRIM_DIVIDE:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 / val2, error);
	}
	break;

	case PRIM_SUBTRACT:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 - val2, error);
	}
	break;

	case PRIM_EQ:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 == val2, error); // TODO(Richo)
	}
	break;

	case PRIM_NEQ:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 != val2, error); // TODO(Richo)
	}
	break;

	case PRIM_GT:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 > val2, error);
	}
	break;

	case PRIM_GTEQ:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 >= val2, error);
	}
	break;

	case PRIM_LT:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 < val2, error);
	}
	break;

	case PRIM_LTEQ:
	{
		float val2 = stack_pop(error);
		float val1 = stack_pop(error);
		stack_push(val1 <= val2, error);
	}
	break;

	case PRIM_NEGATE:
	{
		float val = stack_pop(error);
		stack_push(val == 0 ? 1.0f : 0.0f, error);
	}
	break;

	case PRIM_SIN:
	{
		float val = stack_pop(error);
		stack_push(sinf(val), error);
	}
	break;

	case PRIM_COS:
	{
		float val = stack_pop(error);
		stack_push(cosf(val), error);
	}
	break;

	case PRIM_TAN:
	{
		float val = stack_pop(error);
		stack_push(tanf(val), error);
	}
	break;

	case PRIM_YIELD:
	{
		yieldTime(0, yieldFlag);
	}
	break;

	case PRIM_DELAY_MILLIS:
	{
		int32 time = (int32)stack_pop(error);
		yieldTime(time, yieldFlag);
	}
	break;

	case PRIM_MILLIS:
	{
		float time = (float)millis();
		stack_push(time, error);
	}
	break;

	case PRIM_RET:
	{
		bool returnFromScriptCall = framePointer != 0;
		if (returnFromScriptCall)
		{
			unwindStackAndReturn();
		}
		else
		{
			/*
			INFO(Richo): Jump pass the end of the script so that in the next iteration
			the execution stops.
			*/
			pc = currentScript->getInstructionStop() + 1;
		}
	}
	break;

	case PRIM_POP:
	{
		// Throw value away
		stack_pop(error);
	}
	break;

	case PRIM_RETV:
	{
		uint16 index = framePointer +
			currentScript->getArgCount() +
			currentScript->getLocalCount();

		returnValue = stack_pop(error);

		// TODO(Richo): Duplicated code from PRIM_RET
		bool returnFromScriptCall = framePointer != 0;
		if (returnFromScriptCall)
		{
			unwindStackAndReturn();
		}
		else
		{
			/*
			INFO(Richo): Jump pass the end of the script so that in the next iteration
			the execution stops.
			*/
			pc = currentScript->getInstructionStop() + 1;
		}
	}
	break;

	case PRIM_COROUTINE:
	{
		stack_push(currentCoroutine->scriptIndex, error);
	}
	break;

	case PRIM_LOGICAL_AND:
	{
		float a = stack_pop(error);
		float b = stack_pop(error);
		stack_push(a && b, error);
	}
	break;

	case PRIM_LOGICAL_OR:
	{
		float a = stack_pop(error);
		float b = stack_pop(error);
		stack_push(a || b, error);
	}
	break;

	case PRIM_BITWISE_AND:
	{
		uint32 a = (uint32)stack_pop(error);
		uint32 b = (uint32)stack_pop(error);
		float result = (float)(a & b);
		stack_push(result, error);
	}
	break;

	case PRIM_BITWISE_OR:
	{
		uint32 a = (uint32)stack_pop(error);
		uint32 b = (uint32)stack_pop(error);
		float result = (float)(a | b);
		stack_push(result, error);
	}
	break;

	case PRIM_SERIAL_WRITE:
	{
		uint8 a = (uint8)stack_pop(error);
		monitor->serialWrite(a);
	}
	break;

	case PRIM_ROUND:
	{
		float a = stack_pop(error);
		stack_push((float)round(a), error);
	}
	break;

	case PRIM_CEIL:
	{
		float a = stack_pop(error);
		stack_push((float)ceil(a), error);
	}
	break;

	case PRIM_FLOOR:
	{
		float a = stack_pop(error);
		stack_push((float)floor(a), error);
	}
	break;

	case PRIM_SQRT:
	{
		float a = stack_pop(error);
		stack_push((float)sqrt(a), error);
	}
	break;

	case PRIM_ABS:
	{
		float a = stack_pop(error);
		stack_push((float)fabs(a), error);
	}
	break;

	case PRIM_LN:
	{
		float a = stack_pop(error);
		stack_push((float)log(a), error);
	}
	break;

	case PRIM_LOG10:
	{
		float a = stack_pop(error);
		stack_push(log10f(a), error);
	}
	break;

	case PRIM_EXP:
	{
		float a = stack_pop(error);
		stack_push(expf(a), error);
	}
	break;

	case PRIM_POW10:
	{
		float a = stack_pop(error);
		stack_push(powf(10, a), error);
	}
	break;

	case PRIM_ASIN:
	{
		float a = stack_pop(error);
		stack_push(asinf(a), error);
	}
	break;

	case PRIM_ACOS:
	{
		float a = stack_pop(error);
		stack_push(acosf(a), error);
	}
	break;

	case PRIM_ATAN:
	{
		float a = stack_pop(error);
		stack_push((float)atan(a), error);
	}
	break;

	case PRIM_ATAN2:
	{
		float x = stack_pop(error);
		float y = stack_pop(error);
		stack_push((float)atan2(y, x), error);
	}
	break;

	case PRIM_POWER:
	{
		float b = stack_pop(error);
		float a = stack_pop(error);
		stack_push((float)pow(a, b), error);
	}
	break;

	case PRIM_REMAINDER:
	{
		float b = stack_pop(error);
		float a = stack_pop(error);
		stack_push((float)fmod(a, b), error);
	}
	break;


	case PRIM_RANDOM_INT:
	{
		int32 b = (int32)stack_pop(error);
		int32 a = (int32)stack_pop(error);
		int32 result;
		if (b > a)
		{
			result = random(a, b);
		}
		else
		{
			result = random(b, a);
		}
		stack_push((float)result, error);
	}
	break;

	case PRIM_RANDOM:
	{
		int32 max = 0x7FFFFFFF;
		int32 r1 = (int32)fmod(random(max), max);
		float r2 = (float)((double)r1 / (double)max);
		stack_push(r2, error);
	}
	break;
	case PRIM_SONAR_DIST_CM:
	{
		uint16 maxDist = (uint16)stack_pop(error);
		uint8 echoPin = (uint8)stack_pop(error);
		uint8 trigPin = (uint8)stack_pop(error);

		NewPing sonar(trigPin, echoPin, maxDist);
		uint32 dist = sonar.ping_cm();
		if (dist > 0)
		{
			stack_push((float)dist, error);
		}
		else
		{
			stack_push(INFINITY, error);
		}
	}
	break;

	case PRIM_MATRIX_8x8_DISPLAY:
	{
		uint8 pins_x[8];
		uint8 pins_y[8];
		uint8 rows[8];

		for (int8 i = 7; i >= 0; i--)
		{
			rows[i] = (uint8)stack_pop(error);
		}
		for (int8 i = 7; i >= 0; i--)
		{
			pins_y[i] = (uint8)stack_pop(error);
		}
		for (int8 i = 7; i >= 0; i--)
		{
			pins_x[i] = (uint8)stack_pop(error);
		}

		for (int8 i = 0; i < 8; i++)
		{
			io->setValue(pins_y[i], LOW);
			for (int j = 0; j < 8; j++)
			{
				int32 value = (rows[i] >> j) & 1;
				io->setValue(pins_x[7 - j], (float)value);
			}

			delayMicroseconds(100);

			for (int j = 0; j < 8; j++)
			{
				io->setValue(pins_x[j], LOW);
			}
			io->setValue(pins_y[i], HIGH);
		}
	}
	break;

	case PRIM_TONE:
	{
		float freq = stack_pop(error);
		uint8 pin = (uint8)stack_pop(error);
		io->startTone(pin, freq);
	}
	break;

	case PRIM_NO_TONE:
	{
		uint8 pin = (uint8)stack_pop(error);
		io->stopTone(pin);
	}
	break;

	case PRIM_GET_PIN_MODE:
	{
		uint8 pin = (uint8)stack_pop(error);
		stack_push(io->getMode(pin), error);
	}
	break;

	case PRIM_SET_PIN_MODE:
	{
		uint8 mode = (uint8)stack_pop(error);
		uint8 pin = (uint8)stack_pop(error);
		io->setMode(pin, mode);
	}
	break;

	case PRIM_LCD_INIT0:
	{
		uint8 rows = (uint8)stack_pop(error);
		uint8 cols = (uint8)stack_pop(error);
		uint8 address = (uint8)stack_pop(error);
		
		LiquidCrystal_I2C* lcd = uzi_create(LiquidCrystal_I2C);		
		if (lcd == 0) { error |= OUT_OF_MEMORY; }
		else 
		{
			// HACK(Richo)
			{
				LiquidCrystal_I2C temp(address, cols, rows);
				memcpy(lcd, &temp, sizeof(LiquidCrystal_I2C));
			}
			lcd->init0();
			yieldTime(1000, yieldFlag);
		}
		stack_pushPointer(lcd, error);
	}
	break;

	case PRIM_LCD_INIT1:
	{
		uint32 pointer = (uint32)stack_pop(error);
		if (pointer > 0) 
		{
			LiquidCrystal_I2C* lcd = (LiquidCrystal_I2C*)uzi_pointer(pointer, error);
			lcd->init1();
			lcd->backlight();
		}
		stack_push(pointer, error);
	}
	break;

	case PRIM_LCD_PRINT_VALUE:
	{
		float value = stack_pop(error);
		uint8 line = (uint8)stack_pop(error);
		uint32 pointer = (uint32)stack_pop(error);
		if (pointer > 0) 
		{
			LiquidCrystal_I2C* lcd = (LiquidCrystal_I2C*)uzi_pointer(pointer, error);
			if (error == NO_ERROR)
			{
				lcd->setCursor(0, line);
				lcd->print(value);
			}
		}
	}
	break;

	case PRIM_ARRAY_INIT:
	{
		int32 size = (int32)stack_pop(error);
		if (size > 0) 
		{
			float* array = uzi_createArray(float, size + 1);
			if (array == 0) { error |= OUT_OF_MEMORY; }
			else { array[0] = size; }
			stack_pushPointer(array, error);
		}
		else 
		{
			stack_pushPointer(0, error);
		}
	}
	break;

	case PRIM_ARRAY_GET:
	{
		int32 index = (int32)stack_pop(error);
		uint32 pointer = (uint32)stack_pop(error);

		float result = 0;
		if (pointer > 0 && index >= 0) 
		{
			float* array = (float*)uzi_pointer(pointer, error);
			if (error == NO_ERROR) 
			{
				int32 size = (float)array[0];
				if (index < size)
				{
					result = array[index + 1];
				}
			}
		}
		stack_push(result, error);
	}
	break;


	case PRIM_ARRAY_SET:
	{
		float element = stack_pop(error);
		int32 index = (int32)stack_pop(error);
		uint32 pointer = (uint32)stack_pop(error);

		if (pointer > 0 && index >= 0)
		{
			float* array = (float*)uzi_pointer(pointer, error);
			if (error == NO_ERROR)
			{
				int32 size = (float)array[0];
				if (index < size)
				{
					array[index + 1] = element;
				}
			}
		}
	}
	break;

	}
}

void VM::handleBackwardJump(const int16& argument, bool& yieldFlag)
{
	if (argument < 0)
	{
		if (++loop_count >= 100 || millis() - lastTickStart >= 1)
		{
			yieldTime(0, yieldFlag);
			loop_count = 0;
		}
	}
}

void VM::yieldTime(int32 time, bool& yieldFlag)
{
	currentCoroutine->setNextRun(millis() + time);
	yieldFlag = true;
}

void VM::unwindStackAndReturn(void)
{
	bool returnFromScriptCall = framePointer != 0;
	uint32 value = float_to_uint32(stack_pop(error));
	pc = value & 0xFFFF;
	framePointer = value >> 16;

	// NOTE(Richo): Pop args/locals
	int varCount = currentScript->getArgCount() + currentScript->getLocalCount();
	stack_discard(varCount, error);

	// NOTE(Richo): Only push a return value if we were called from another script
	if (returnFromScriptCall && currentScript->type == FUNC)
	{
		stack_push(returnValue, error);
	}

	// NOTE(Richo): Set current script and increment pc
	Script* script = currentProgram->getScriptForPC(pc);
	
	// HACK(Richo): I don't like this, there shouldn't be any reason for not finding a script if the pc was valid.
	// I had to add this here because of a test that requires that a program with a single empty task doesn't break
	// the VM. However, it doesn't feel right. Maybe I should just change the dead code remover to ensure empty tasks
	// are removed. That's probably the better solution...
	if (script)
	{
		currentScript = script;
	}

	// HACK(Richo): I *REALLY* don't like this. I had to change the meaning of the pc we push to the stack to be the
	// previous instruction (usually a SCRIPT_CALL) because if we kept pushing the next instruction we could arrive
	// after the end of the calling script and we would be in trouble. This situation couldn't happen before because 
	// script calls were never the last instruction on a script, if they were in a statement position then the compiler
	// used to emit a "pop" so we were safe. However, now that is no longer the case and we have this issue. I would 
	// like to always push the previous pc but if the framePointer == -1 it means we're starting executing this script
	// so, as a special case, we push the first pc of the script (thus, we can't increment it or we would be off by 1).
	if (framePointer != -1) 
	{
		pc++;
	}
}

void VM::reset()
{
	halted = false;
	haltedScript = 0;
	framePointer = 0;
	pc = 0;
	currentProgram = 0;
	currentCoroutine = 0;
	currentScript = 0;
}
