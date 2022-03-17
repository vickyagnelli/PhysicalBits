let UziCode = (function () {

  let editor;
  let focus = false;
  let updating = false;
	let markers = [];
  let observers = {
    "change": [],
    "cursor": []
  };

  function init() {

    editor = ace.edit("code-editor");
    editor.setTheme("ace/theme/ambiance");
    editor.getSession().setMode("ace/mode/uzi");

    editor.selection.on("changeCursor", handleCursorChange)
    editor.on("focus", function () { 
      focus = true; 
      handleCursorChange();
    });
    editor.on("blur", function () { focus = false; });
    editor.on("change", function (e) {
      trigger("change", focus);
      
      if (updating) return;

      let start = e.start.row;
      let delta = e.lines.length - 1;
      if (e.action == "insert") {
        delta *= 1;
      } else if (e.action == "remove") {
        delta *= -1;
      } else {
        debugger;
      }

      /*
      TODO(Richo): Here we should update the validBreakpoints list to insert
      null in every inserted line. Otherwise everything gets out of sync...
      */
      let breakpoints = Debugger.getBreakpoints();
      let bpts = breakpoints.filter(function (bp) { return bp > start; });
      breakpoints = breakpoints.filter(function (bp) { return bp <= start; });
      bpts.forEach(function (bp) { breakpoints.push(bp + delta); });
      editor.session.clearBreakpoints();
      breakpoints.forEach(function (line) {
        editor.session.setBreakpoint(line, "breakpoint");
      });
      Debugger.setBreakpoints(breakpoints);
    });

		$(".ace_gutter").on("click", function (e) {
      if (editor.getValue() !== Uzi.state.program.src) return;
      var line = Debugger.getValidLineForBreakpoint(Number.parseInt(e.target.innerText) - 1);
      Debugger.toggleBreakpoint(line);
      editor.gotoLine(line + 1);
		});

    Uzi.on("update", function (state, previousState, keys) {
      updating = true;
      try {
        if (keys.has("program")) {
          handleProgramUpdate(state, previousState);
        }
      } catch (err) {
        console.error(err);
      } finally {
        updating = false;
      }
    });

    Debugger.on("change", handleDebuggerUpdate);
  }

  function handleCursorChange() {
    if (!focus) return;
    let doc = editor.session.getDocument();

    let col = editor.selection.cursor.column;
    let row = editor.selection.cursor.row;
    let idx = doc.positionToIndex({row: row, column: col});
    trigger("cursor", idx);
  }
  
  function handleProgramUpdate(state, previousState) {
    if (focus) return; // Don't change the code while the user is editing!
    if (state.program.type == "uzi") return; // Ignore textual programs
    if (editor.getValue() !== "" &&
        state.program.src == previousState.program.src) return;

    let src = state.program.src;
    if (src == undefined) return;
    if (editor.getValue() !== src) {
      editor.setValue(src, 1);

      // TODO(Richo): How do we preserve the breakpoints after a program update?
      breakpoints = [];
      editor.session.clearBreakpoints();
      markers.forEach(function (each) { editor.session.removeMarker(each); });
    }
  }

  function handleDebuggerUpdate(state, stackFrameIndex) {
    try {
      if (!state.debugger.isHalted) {
        let src = state.program.src;
        if (editor.getValue() !== src) {
          editor.setValue(src, 1);
        }
      }

      let interval = null;
      let src = state.program.src;
      if (state.debugger.stackFrames.length > 0) {
        let stackFrame = state.debugger.stackFrames[stackFrameIndex];
        src = state.debugger.sources[stackFrame.source];
        interval = stackFrame.interval;
      }
      
      if (editor.getValue() !== src) {
        editor.setValue(src, 1);
      }
      highlight(interval);

      breakpoints = state.debugger.breakpoints;
      editor.session.clearBreakpoints();
      if (src == state.program.src) {
        breakpoints.forEach(function (line) {
          editor.session.setBreakpoint(line, "breakpoint");
        });
      }
    } catch (err) {
      console.log(err);
    }
  }

  function highlight(interval) {
		markers.forEach((each) => { editor.session.removeMarker(each); });
		if (interval == null) {
			markers = [];
		} else {
			let doc = editor.session.getDocument();
			let start = doc.indexToPosition(interval[0]);
			let end = doc.indexToPosition(interval[1]);
			let range = new ace.Range(start.row, start.column, end.row, end.column);
			markers = [];
			markers.push(editor.session.addMarker(range, "debugger_ActiveLine", "line", true));
			markers.push(editor.session.addMarker(range, "debugger_ActiveInterval", "line", true));
		}
  }

  function select(interval) {
    if (focus) return;
    if (interval == null || interval.length < 2) {
      editor.clearSelection();
    } else {
      let doc = editor.session.getDocument();
      let start = doc.indexToPosition(interval[0]);
      let end = doc.indexToPosition(interval[1]);
      let range = new ace.Range(start.row, start.column, end.row, end.column);
      editor.selection.setSelectionRange(range);
    }
  }

  function resizeEditor() {
    if (editor) {// TODO(Richo): Is this condition necessary??
      editor.resize(true);
    }
  }

  function setProgram(code) {
    editor.setValue(code);
    return true;
  }

  function getProgram() {
    return editor.getValue();
  }

  function clearEditor() {
    editor.setValue("");
  }

  function on (evt, callback) {
    observers[evt].push(callback);
  }

  function trigger(evt, args) {
    observers[evt].forEach(function (fn) {
      try {
        fn(args);
      } catch (err) {
        console.error(err);
      }
    });
  }

  return {
    init: init,
    on: on,
    resizeEditor: resizeEditor,
    setProgram: setProgram,
    getProgram: getProgram,
    clearEditor: clearEditor,

    handleDebuggerUpdate: handleDebuggerUpdate,
    select: select,

    getEditor: () => editor,
  }
})();
