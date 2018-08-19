
$("#compile").on("click", function () {
	Uzi.compile(editor.getValue(), "text", function (bytecodes) {			
		console.log(bytecodes);
		Alert.success("Compilation successful");
	});
});

$("#install").on("click", function () {
	Uzi.install(editor.getValue(), "text", function (bytecodes) {			
		console.log(bytecodes);
		Alert.success("Installation successful");
	});
});

$("#run").on("click", function () {
	Uzi.run(editor.getValue(), "text", function (bytecodes) {			
		console.log(bytecodes);
	});
});

Uzi.onConnectionUpdate(function () {
	if (Uzi.isConnected) {				
		$("#install").removeAttr("disabled");
		$("#run").removeAttr("disabled");
		$("#more").removeAttr("disabled");
		
		if (editor.getValue() !== Uzi.program.src) {
			editor.setValue(Uzi.program.src);
		}
	} else {
		$("#install").attr("disabled", "disabled");
		$("#run").attr("disabled", "disabled");
		$("#more").attr("disabled", "disabled");
	}
});