let IDE = (function () {
  const electron = require('electron');
  const { dialog } = electron ? electron.remote : {};
  const fs = require('fs');

  let selectedPort = DEMO ? "simulator" : "automatic";
  let autorunInterval, autorunNextTime, autorunCounter = 0;
  let dirtyBlocks, dirtyCode;
  let lastProgram = { code: "", type: "uzi" };
  let outputHistory = [];

  let userPorts = [];

  // HACK(Richo): To disable some controls while we're waiting for a connection
  let connecting = false;

  let IDE = {
    init: function () {
      // NOTE(Richo): The following tasks need to be done in order:
      return initializeLayout()
        .then(initializeCodePanel)
        .then(initializeBlocksPanel)
        .then(initializeBlocklyMotorsModal)
        .then(initializeBlocklySonarsModal)
        .then(initializeBlocklyJoysticksModal)
        .then(initializeBlocklyVariablesModal)
        .then(initializeBlocklyListsModal)
        .then(initializeAutorun)
        .then(initializeTopBar)
        .then(initializeInspectorPanel)
        .then(initializeOutputPanel)
        .then(initializePlotterPanel)
        .then(initializeDebuggerPanel)
        .then(initializeBrokenLayoutErrorModal)
        .then(initializeServerNotFoundErrorModal)
        .then(initializeOptionsModal)
        .then(initializeElog);
    },
  };

  function initializeElog() {    
    let buttons = ["input", "a", "button", "select"];
    let handled = new Set();
    $(buttons.join(",")).on("click", function () {
      if (!this.id || handled.has(this.id)) return;
      handled.add(this.id);
      Uzi.elog("UI/CLICK", "#" + this.id);
    });
    $(window).click(function (e) {
      let element = e.target;
      setTimeout(function () {
        try {
          if (handled.has(element.id)) return;
    
          // Inspector "eyes"
          if (element.classList.contains("fa-eye")) {
            if (element.id.startsWith("pin")) {
              Uzi.elog("UI/PIN_EYE_CLICK", "#" + element.id);
            } else if (element.id.startsWith("global")) {
              Uzi.elog("UI/GLOBAL_EYE_CLICK", "#" + element.id);
            }
            return;
          }
    
          // Special case for empty panel
          if (element.classList.contains("lm_content")) {
            let firstChild = element.childNodes[0];
            if (firstChild && firstChild.id) {
              Uzi.elog("UI/CLICK", "#" + firstChild.id);
              return;
            }
          }
    
          // Special case for labels (to avoid logging twice)
          if (element.localName == "label") {
            let target = document.getElementById(element.getAttribute("for"));
            if (target && buttons.includes(target.localName)) {
              if (target.type == "checkbox" && !handled.has(target.id)) {
                Uzi.elog("UI/CLICK", "#" + target.id);
              }
              return;
            }
          }
          
          // Fallback: look up in the parent chain
          while (!element.id || element.id.startsWith(":")) {
            element = element.parentNode;
            if (!element || element == document.body) return;
          }
          if (buttons.includes(element.localName) || handled.has(element.id)) return;
          Uzi.elog("UI/CLICK", "#" + element.id);
        } catch {
          // Do nothing?
        } finally {
          handled.clear(); 
        }
      }, 0);
    });
  }

  function initializeLayout() {
    return LayoutManager.init(() => {
      resizePanels();
      saveToLocalStorage();
      checkBrokenLayout();
      updateVisiblePanelsInOptionsModal();

      // HACK(Richo): The following allows me to translate panel titles
      $(".lm_title").each(function () { $(this).attr("lang", "en"); });
      i18n.updateUI();
    });
  }

  function initializeBlocksPanel() {
    return UziBlock.init()
      .then(() => {
        let lastProgram = undefined;

        UziBlock.on("select", block => {
          UziCode.select(Uzi.state.program["block->token"][block]); 
        });
        UziBlock.on("change", userInteraction => {
          saveToLocalStorage();

          /*
          NOTE(Richo): Only trigger autorun if the blocks were manually changed by
          the user. This prevents a double compilation when changing the program
          from the code editor.
          */
          if (userInteraction && !UziCode.isFocused()) {
            let currentProgram = getBlocklyCode();
            if (currentProgram !== lastProgram) {
              lastProgram = currentProgram;

              dirtyBlocks = true;
              dirtyCode = false;

              scheduleAutorun(false, "BLOCKS CHANGE!");
            }
          }
        });

        Debugger.on("change", (state, stackFrameIndex) => {
          /*
          HACK(Richo): FUCKING Blockly! I wasted a lot of time debugging this problem in which
          a debugger update from the server wasn't being applied to the block editor (a breakpoint
          was removed but the block still had the warning text set). By trial and error I discover
          that, for some reason, while I'm dragging a block (a different block, completely unrelated
          with the one misbehaving) the warning texts are not applied. I don't know why setting a
          timeout to execute the update fixes the issue, though. But it seems it does so I will
          keep this. Again, fucking Blockly...
          */
          setTimeout(UziBlock.handleDebuggerUpdate(state, stackFrameIndex), 0);
        });
        Uzi.on("update", (state, previousState, keys) => {
          if (state.program.type == "json") return; // Ignore blockly programs
          if (state.program.src == previousState.program.src) return;
          let blocklyProgram = ASTToBlocks.generate(state.program);
          UziBlock.setProgram(blocklyProgram);
          UziBlock.cleanUp();
        });
      })
      .then(restoreFromLocalStorage);
  }

  function loadDefaultProgram() {
    return ajax.GET("default.phb")
      .then(contents => {
        loadSerializedProgram(contents);
        scheduleAutorun(false, "DEFAULT PROGRAM!");
      });
  }

  function initializeTopBar() {
    if (electron) {
      $("#save-button").show();
      $("#save-as-button").show();
      $("#download-button").hide();
    } else {
      $("#save-button").hide();
      $("#save-as-button").hide();
      $("#download-button").show();
    }
    $("#new-button").on("click", newProject);
    $("#open-button").on("click", openProject);
    $("#save-button").on("click", saveProject);
    $("#save-as-button").on("click", saveAsProject);
    $("#download-button").on("click", downloadProject);

    $("#program-error").on("click", verify);

    $("#port-dropdown").change(choosePort);
    $("#connect-button").on("click", connect);
    $("#disconnect-button").on("click", disconnect);
    if (Uzi.state.features["interactivity?"]) {
      $("#connect-button").show();  
    } else {
      $("#connect-button").hide();
    }

    $("#verify-button").on("click", verify);
    $("#run-button").on("click", run);
    $("#install-button").on("click", install);
		$("#interactive-checkbox").on("change", toggleInteractive);
    $("#options-button").on("click", openOptionsDialog);
    if (Uzi.state.features["options-button?"]) {
      $("#options-button").show();
    } else {
      $("#options-button").hide();
    }
    Uzi.on("update", updateTopBar);
    Uzi.on("update", updateConnection);
    updatePortDropdown();
  }

  function initializeInspectorPanel() {
    $("#pin-choose-button").on("click", openInspectorPinDialog);
    $("#global-choose-button").on("click", openInspectorGlobalDialog);
    let editing = new Set();
    Uzi.on("update", () => updateInspectorPanel(editing));
  }

  function initializeBlocklyMotorsModal() {

    function getUsedMotors() {
      let program = Uzi.state.program;
      if (program == null) return new Set();
      // HACK(Richo): We are actually returning all the aliases, not just motors
      return new Set(program.ast.imports.map(imp => imp.alias));
    }

    UziBlock.getWorkspace().registerButtonCallback("configureDCMotors", () => {
      let motors = UziBlock.getMotors();
      let usedMotors = getUsedMotors();
      let spec = {
        title: i18n.translate("Configure motors"),
        cantRemoveMsg: i18n.translate("This motor is being used by the program!"),
        defaultElement: { name: "motor", enable: "D10", fwd: "D9", bwd: "D8" },
        columns: [
          {id: "name", type: "identifier", name: i18n.translate("Motor name")},
          {id: "enable", type: "pin", name: i18n.translate("Enable pin")},
          {id: "fwd", type: "pin", name: i18n.translate("Forward pin")},
          {id: "bwd", type: "pin", name: i18n.translate("Backward pin")},
        ],
        rows: motors.map(each => {
          let clone = deepClone(each);
          clone.removable = !usedMotors.has(each.name);
          return clone;
        })
      };
      Uzi.elog("BLOCKLY/MODAL_OPEN", {type: "motors"});
      BlocklyModal.show(spec).then(data => {
        Uzi.elog("BLOCKLY/MODAL_CLOSE", {type: "motors", data: data});
        UziBlock.setMotors(data);
        UziBlock.refreshToolbox();
        saveToLocalStorage();
        scheduleAutorun(true, "MOTOR UPDATE!");
      });
    });
  }

  function initializeBlocklySonarsModal() {

    function getUsedSonars() {
      let program = Uzi.state.program;
      if (program == null) return new Set();
      // HACK(Richo): We are actually returning all the aliases, not just sonars
      return new Set(program.ast.imports.map(imp => imp.alias));
    }

    UziBlock.getWorkspace().registerButtonCallback("configureSonars", () => {
      let sonars = UziBlock.getSonars();
      let usedSonars = getUsedSonars();
      let spec = {
        title: i18n.translate("Configure sonars"),
        cantRemoveMsg: i18n.translate("This sonar is being used by the program!"),
        defaultElement: { name: "sonar", trig: "D11", echo: "D12", maxDist: "200" },
        columns: [
          {id: "name", type: "identifier", name: i18n.translate("Sonar name")},
          {id: "trig", type: "pin", name: i18n.translate("Trig pin")},
          {id: "echo", type: "pin", name: i18n.translate("Echo pin")},
          {id: "maxDist", type: "number", name: i18n.translate("Max distance (cm)")},
        ],
        rows: sonars.map(each => {
          let clone = deepClone(each);
          clone.removable = !usedSonars.has(each.name);
          return clone;
        })
      };
      Uzi.elog("BLOCKLY/MODAL_OPEN", {type: "sonars"});
      BlocklyModal.show(spec).then(data => {
        Uzi.elog("BLOCKLY/MODAL_CLOSE", {type: "sonars", data: data});
        UziBlock.setSonars(data);
        UziBlock.refreshToolbox();
        saveToLocalStorage();
        scheduleAutorun(true, "SONAR UPDATE!");
      });
    });
  }

  function initializeBlocklyJoysticksModal() {

    function getUsedJoysticks() {
      let program = Uzi.state.program;
      if (program == null) return new Set();
      // HACK(Richo): We are actually returning all the aliases, not just joysticks
      return new Set(program.ast.imports.map(imp => imp.alias));
    }

    UziBlock.getWorkspace().registerButtonCallback("configureJoysticks", () => {
      let joysticks = UziBlock.getJoysticks();
      let usedJoysticks = getUsedJoysticks();
      let spec = {
        title: i18n.translate("Configure joysticks"),
        cantRemoveMsg: i18n.translate("This joystick is being used by the program!"),
        defaultElement: { name: "joystick", xPin: "A0", yPin: "A1" },
        columns: [
          {id: "name", type: "identifier", name: i18n.translate("Joystick name")},
          {id: "xPin", type: "pin", name: i18n.translate("X pin")},
          {id: "yPin", type: "pin", name: i18n.translate("Y pin")},
        ],
        rows: joysticks.map(each => {
          let clone = deepClone(each);
          clone.removable = !usedJoysticks.has(each.name);
          return clone;
        })
      };
      Uzi.elog("BLOCKLY/MODAL_OPEN", {type: "joysticks"});
      BlocklyModal.show(spec).then(data => {
        Uzi.elog("BLOCKLY/MODAL_CLOSE", {type: "joysticks", data: data});
        UziBlock.setJoysticks(data);
        UziBlock.refreshToolbox();
        saveToLocalStorage();
        scheduleAutorun(true, "JOYSTICK UPDATE!");
      });
    });
  }

  function initializeBlocklyVariablesModal() {
    UziBlock.getWorkspace().registerButtonCallback("configureVariables", () => {
      let variables = UziBlock.getVariables();
      let usedVariables = UziBlock.getUsedVariables();
      let spec = {
        title: i18n.translate("Configure variables"),
        cantRemoveMsg: i18n.translate("This variable is being used by the program!"),
        defaultElement: {name: "variable", value: "0"},
        columns: [
          {id: "name", type: "identifier", name: i18n.translate("Variable name")},
          {id: "value", type: "numberOrPin", name: i18n.translate("Initial value (if global)")},
        ],
        rows: variables.map(each => {
          let clone = deepClone(each);
          clone.removable = !usedVariables.has(each.name);
          return clone;
        })
      };
      Uzi.elog("BLOCKLY/MODAL_OPEN", {type: "variables"});
      BlocklyModal.show(spec).then(data => {
        Uzi.elog("BLOCKLY/MODAL_CLOSE", {type: "variables", data: data});
        UziBlock.setVariables(data);
        UziBlock.refreshToolbox();
        saveToLocalStorage();
        scheduleAutorun(true, "VARIABLE UPDATE!");
      });
    });
  }

  function initializeBlocklyListsModal() {

    function getUsedLists() {
      let program = Uzi.state.program;
      if (program == null) return new Set();
      // HACK(Richo): We are actually returning all the aliases, not just lists
      return new Set(program.ast.imports.map(imp => imp.alias));
    }

    UziBlock.getWorkspace().registerButtonCallback("configureLists", () => {
      let lists = UziBlock.getLists();
      let usedLists = getUsedLists();
      let spec = {
        title: i18n.translate("Configure lists"),
        cantRemoveMsg: i18n.translate("This list is being used by the program!"),
        defaultElement: { name: "list", size: "10" },
        columns: [
          {id: "name", type: "identifier", name: i18n.translate("List name")},
          {id: "size", type: "number", name: i18n.translate("Capacity")},
        ],
        rows: lists.map(each => {
          let clone = deepClone(each);
          clone.removable = !usedLists.has(each.name);
          return clone;
        })
      };
      Uzi.elog("BLOCKLY/MODAL_OPEN", {type: "lists"});
      BlocklyModal.show(spec).then(data => {
        Uzi.elog("BLOCKLY/MODAL_CLOSE", {type: "lists", data: data});
        UziBlock.setLists(data);
        UziBlock.refreshToolbox();
        saveToLocalStorage();
        scheduleAutorun(true, "LIST UPDATE!");
      });
    });
  }

  function initializeCodePanel() {
    UziCode.init();
    UziCode.on("cursor", idx => {
      UziBlock.selectByIndex(idx);
    })
    UziCode.on("change", focus => {
      saveToLocalStorage();
      
      if (focus) {
        dirtyCode = true;
        dirtyBlocks = false;
        scheduleAutorun(false, "CODE CHANGE!");
      }
    });
  }

  function initializeOutputPanel() {
    Uzi.on("update", () => {
      Uzi.state.output.forEach(appendToOutput);
      Uzi.state.output = [];
    });

    i18n.on("change", () => {
      $("#output-console").html("");
      let temp = outputHistory;
      outputHistory = [];
      temp.forEach(appendToOutput);
    })
  }

  function initializePlotterPanel() {
    Plotter.init();
    Uzi.on("update", Plotter.update);
  }

  function initializeDebuggerPanel() {
    Debugger.init();
    Uzi.on("update", Debugger.update);
  }

  function initializeAutorun() {
    const interval = 10;
    function loop() {
      autorun().finally(() => {
        setTimeout(loop, interval);
      });
    }
    setTimeout(loop, interval);
  }

  function initializeBrokenLayoutErrorModal() {
    $("#fix-broken-layout-button").on("click", () => {
      LayoutManager.reset();
      $("#broken-layout-modal").modal("hide");
    });
  }

  function initializeServerNotFoundErrorModal() {
    Uzi.on("server-disconnect", () => {
      $("#server-not-found-modal").modal('show');
    });
    setInterval(() => {
      if (Uzi.serverAvailable) {
        $("#server-not-found-modal").modal('hide');
      }
    }, 1000);
  }

  function initializeOptionsModal() {
    $("#restore-layout-button").on("click", LayoutManager.reset);
    $("#uzi-syntax-checkbox").on("change", updateUziSyntax);
    $("#adv-blocks-checkbox").on("change", updateAdvBlocks);
    $("#all-caps-checkbox").on("change", updateAllCaps);
    if (!fs) {
      $("#autosave-checkbox").attr("disabled", "disabled");
    } else {
      $("#autosave-checkbox").on("change", toggleAutosave);
    }

    $('input[name="language-radios"]:radio').change(function () {
      i18n.currentLocale(this.value);
    });
    i18n.on("update", function () {
      let locale = i18n.currentLocale();
      $('input[name="language-radios"]:radio').each(function () {
        let val = $(this).val();
        if (locale.startsWith(val)) {
          $(this).prop("checked", true);
        }
      });
      console.log(locale);
    });
  }

  function updateVisiblePanelsInOptionsModal() {
    $('input[name="layout-panels"]').each(function () {
      let panelId = $(this).val();
      $(this).prop("checked", $(panelId).is(":visible"));
    });
  }

  function checkBrokenLayout() {
    if (!LayoutManager.isBroken()) return;

    setTimeout(function () {
      if (!LayoutManager.isBroken()) return;
      $("#broken-layout-modal").modal("show");
    }, 1000);
  }

  function appendToOutput(entry) {
    // Remember the entry in case we need to update the panel (up to a fixed limit)
    if (outputHistory.length == 100) { outputHistory.shift(); }
    outputHistory.push(entry);

    // Special case for the clear message
    if (entry.type == "clear") {
      $("#output-console").html("");
      outputHistory = [];
      return;
    }

    // Translate and format the message
    let type = entry.type || "info";
    let args = entry.args || [];
    let regex = /%(\d+)/g;
    let text = i18n.translate(entry.text).replace(regex, (m, i) => {
      let arg = args[parseInt(i) - 1];
      return arg || m;
    });

    // Append element
    let css = {
      info: "text-white",
      success: "text-success",
      error: "text-danger",
      warning: "text-warning"
    };
    let el = $("<div>").addClass("small").addClass(css[type]);
    if (text) { el.text(text); }
    else { el.html("&nbsp;"); }
    $("#output-console").append(el);

    // Scroll to bottom
    let panel = $("#output-panel").get(0);
    panel.scrollTop = panel.scrollHeight - panel.clientHeight;
  }

  function resizePanels() {
    UziBlock.resizeWorkspace();
    UziCode.resizeEditor();
    Plotter.resize();
  }

  function saveToFile(path) {
    function errorHandler(err) {
      $("#file-dirty").hide();
      $("#file-saving").hide();
      $("#file-saved").hide();
      console.log(err);
      appendToOutput({text: "Error attempting to write the project file", type: "error"});
    }
    function getSerializedProgram() {
      let data = {
        blockly: UziBlock.getProgram(),
        code: getTextualCode(),
      };
      let json = JSONX.stringify(data);
      return json;
    }

    if (fs) {
      function saving() {
        if ($("#file-saved").is(":visible")) return;
        $("#file-saving").show();
        $("#file-saved").hide();
      }

      function saved() {
        $("#file-dirty").hide();
        $("#file-saving").hide();
        $("#file-saved").show();
        setTimeout(() => {
          $("#file-saving").hide();
          $("#file-saved").hide();
        }, 1000);
      }

      saving();
      let program = getSerializedProgram();
      fs.promises.writeFile(path, program, "utf8").then(saved).catch(errorHandler);
    } else {
      try {
        let program = getSerializedProgram();
        let blob = new Blob([program], {type: "text/plain;charset=utf-8"});
        saveAs(blob, path, { autoBom: false });
      } catch (err) {
        errorHandler(err);
      }
    }
  }

	function restoreFromLocalStorage() {
    try {
      let ui = {
        settings: JSONX.parse(localStorage["uzi.settings"] || "null"),
        fileName: localStorage["uzi.fileName"] || "",
        layout: JSONX.parse(localStorage["uzi.layout"] || "null"),
        blockly: JSONX.parse(localStorage["uzi.blockly"] || "null"),
        code: localStorage["uzi.code"],
        ports: JSONX.parse(localStorage["uzi.ports"] || "null"),
      };
      setUIState(ui);
      if (ui.fileName == "" && ui.blockly == null && ui.code == null) {
        loadDefaultProgram();
      } else {
        scheduleAutorun(false, "LOCALSTORAGE RESTORE!");
      }
    } catch (err) {
      console.log(err);
    }
	}

  function saveToLocalStorage() {
    if (UziBlock.getWorkspace() == undefined) return;
    if (LayoutManager.getLayoutConfig() == undefined) return;

    let ui = getUIState();
    localStorage["uzi.settings"] = JSONX.stringify(ui.settings);
    localStorage["uzi.fileName"] = ui.fileName;
    localStorage["uzi.layout"] = JSONX.stringify(ui.layout);
    localStorage["uzi.blockly"] = JSONX.stringify(ui.blockly);
    localStorage["uzi.code"] = ui.code;
    localStorage["uzi.ports"] = JSONX.stringify(ui.ports);

    if ($("#autosave-checkbox").get(0).checked && $("#file-name").text()) {
      saveProject();
    }
  }

  function getUIState() {
    return {
      settings: {
        autosave:    $("#autosave-checkbox").get(0).checked,
        interactive: $("#interactive-checkbox").get(0).checked,
        allcaps:     $("#all-caps-checkbox").get(0).checked,
        uziSyntax:   $("#uzi-syntax-checkbox").get(0).checked,
        advBlocks:   $("#adv-blocks-checkbox").get(0).checked,
      },
      fileName:  $("#file-name").text() || "",
      layout: LayoutManager.getLayoutConfig(),
      blockly: UziBlock.getProgram(),
      code: getTextualCode(),
      ports: {
        selectedPort: selectedPort,
        userPorts: userPorts
      }
    };
  }

  function setUIState(ui) {
    try {
      if (ui.settings != undefined) {
        $("#autosave-checkbox").get(0).checked    = ui.settings.autosave;
        $("#interactive-checkbox").get(0).checked = ui.settings.interactive;
        $("#all-caps-checkbox").get(0).checked    = ui.settings.allcaps;
        $("#uzi-syntax-checkbox").get(0).checked  = ui.settings.uziSyntax;
        $("#adv-blocks-checkbox").get(0).checked  = ui.settings.advBlocks;
      } else {
        $("#autosave-checkbox").get(0).checked    = false;
        $("#interactive-checkbox").get(0).checked = true;
        $("#all-caps-checkbox").get(0).checked    = false;
        $("#uzi-syntax-checkbox").get(0).checked  = false;
        $("#adv-blocks-checkbox").get(0).checked  = false;
      }
      updateAllCaps();
      updateUziSyntax();
      updateAdvBlocks();

      if (ui.fileName != undefined) {
        $("#file-name").text(ui.fileName);
      }

      if (ui.layout != undefined) {
        if (Uzi.state.features["persistent-layout?"]) {
          LayoutManager.setLayoutConfig(ui.layout);
        }
      }

      if (ui.blockly != undefined) {
        dirtyBlocks = UziBlock.setProgram(ui.blockly);
      }

      if (ui.code != undefined) {
        dirtyCode = UziCode.setProgram(ui.code);
      }

      if (ui.ports != undefined) {
        selectedPort = ui.ports.selectedPort;
        userPorts = ui.ports.userPorts;
        updatePortDropdown();
      }
    } catch (err) {
      console.error(err);
    }
  }

  function newProject() {
    MessageBox.confirm(i18n.translate("Beware!"),
                       i18n.translate("You will lose all your unsaved changes. Are you sure?"),
                       MessageBox.ICONS.warning).then(ok => {
      if (ok) {
        $("#file-name").text("");
    		UziBlock.getWorkspace().clear();
        scheduleAutorun(true, "NEW PROJECT!");
      }
    });
  }

  function loadSerializedProgram(contents) {
    function readFirst(program, selectors) {
      for (let i = 0; i < selectors.length; i++) {
        let value = program[selectors[i]];
        if (value != undefined) return value;
      }
      return undefined;
    }

    let program = JSONX.parse(contents);
    let blockly = readFirst(program, ["blockly", "blocks"]);
    let code = readFirst(program, ["code", "program"]);

    if (blockly != undefined) {
      dirtyBlocks = UziBlock.setProgram(blockly);
    }

    if (code != undefined) {
      dirtyCode = UziCode.setProgram(code);
    }

    if (!dirtyBlocks) {
      UziBlock.getWorkspace().clear();
    }
    if (!dirtyCode) {
      UziCode.clearEditor();
    }
  }

  function openProject() {
    function errorHandler(err) {
      console.log(err);
      appendToOutput({text: "Error attempting to read the project file", type: "error"});
    }

    // HACK(Richo): Clearing this field will temporarily disable the autosave.
    // TODO(Richo): This is a mess!!
    $("#file-name").text("");

    function load(path, contents) {
      try {
        loadSerializedProgram(contents);
        $("#file-name").text(path);
        scheduleAutorun(false, "OPEN PROJECT!");
      } catch (err) {
        errorHandler(err);
    		UziBlock.getWorkspace().clear();
        UziCode.clearEditor();
        $("#file-name").text("");
        scheduleAutorun(true, "OPEN PROJECT!");
      }
    }

    if (dialog && fs) {
      dialog.showOpenDialog({
        filters: [{name: "Physical Bits project", extensions: ["phb"]}],
        properties: ["openFile"]
      }).then(response => {
        if (!response.canceled) {
          let path = response.filePaths[0];
          fs.promises.readFile(path, "utf8")
            .then(contents => load(path, contents))
            .catch(errorHandler);
        }
      });
    } else {
      let input = $("#open-file-input").get(0);
      input.onchange = function () {
        let file = input.files[0];
        input.value = null;
        if (file == undefined) return;

        let reader = new FileReader();
        reader.onload = function(e) {
          load(file.name, e.target.result);
        };
        reader.readAsText(file);
      };
      input.click();
    }
  }

  function saveProject() {
    let path = $("#file-name").text();
    if (!path) { saveAsProject(); }
    else {
      saveToFile(path);
    }
  }

  function saveAsProject() {
    if (!dialog) return;
    dialog.showSaveDialog({
      defaultPath: $("#file-name").text() || "program.phb",
      filters: [{name: "Physical Bits project", extensions: ["phb"]}],
      properties: ["openFile"]
    }).then(response => {
      if (!response.canceled) {
        let path = response.filePath;
        $("#file-name").text(path);
        saveToFile(path);
      }
    })
  }

  function downloadProject() {
    MessageBox.prompt(i18n.translate("Save project"),
                      i18n.translate("File name:"),
                      $("#file-name").text() || "program.phb").then(fileName => {
      if (fileName == undefined) return;
      if (!fileName.endsWith(".phb")) {
        fileName += ".phb";
      }
      $("#file-name").text(fileName);
      saveToFile(fileName);
    });
  }

  function choosePort() {
    let value = $("#port-dropdown").val();
    if (value == "other") {
      let defaultOption = selectedPort == "automatic" ? "" : selectedPort;
      MessageBox.prompt(i18n.translate("Choose port"),
                        i18n.translate("Port name:"),
                        defaultOption).then(value => {
        if (!value) { value = selectedPort; }
        else if (userPorts.indexOf(value) < 0) {
          userPorts.push(value);
        }
        setSelectedPort(value);
        saveToLocalStorage();
      });
    } else {
      setSelectedPort(value);
      saveToLocalStorage();
    }
  }

  function setSelectedPort(val) {
    selectedPort = val;
    if ($("#port-dropdown option[value='" + selectedPort + "']").length <= 0) {
      // HACK(Richo): Add the special "simulator" option in case it wasn't there
      if (val == "simulator") {
        $("<option>")
          .text("Simulator")
          .attr("value", selectedPort)
          .attr("lang", "en")
          .insertBefore("#port-dropdown-divider");
        i18n.updateUI();
      } else {
        $("<option>")
          .text(selectedPort)
          .attr("value", selectedPort)
          .insertBefore("#port-dropdown-divider");
      }
    }
    $("#port-dropdown").val(selectedPort);
  }

  function connect() {
    if (DEMO && selectedPort != "simulator") {
      MessageBox.alert("DEMO version", "The functionality you requested is not implemented in the DEMO version. Please download Physical Bits to enjoy all its capabilities");
      return Promise.reject("DEMO version does not support arbitrary connections");
    } else {
      connecting = true;
      $("#connect-button").attr("disabled", "disabled");
      $("#port-dropdown").attr("disabled", "disabled");
      if (selectedPort == "automatic") {
        let availablePorts = Uzi.state.connection.availablePorts;
        if (availablePorts.length == 0) {
          appendToOutput({text: "No available ports found", type: "error"});
          connecting = false;
          updateTopBar();
          return Promise.reject("No available ports found");
        } else {
          return attemptConnection(availablePorts);
        }
      } else {
        return Uzi.connect(selectedPort).finally(() => {
          connecting = false;
          updateTopBar();
        });
      }
    }
  }

  function attemptConnection(availablePorts) {
    let port = availablePorts.shift();
    return Uzi.connect(port).then(data => {
      if (data == port) {
        selectedPort = port;
        if (selectedPort) { saveToLocalStorage(); }
        connecting = false;
        return port;
      } else if (availablePorts.length > 0) {
        return attemptConnection(availablePorts);
      } else {
        connecting = false;
        return null;
      }
    }).catch(() => { connecting = false; });
  }
  window.attemptConnection = attemptConnection;

  function disconnect() {
    connecting = true;
    $("#disconnect-button").attr("disabled", "disabled");
    Uzi.disconnect().finally(() => {
      connecting = false;
      updateTopBar();
    });
  }

  function evalProgramFn(fn) {
    let program = lastProgram.code;
    let type = lastProgram.type;
    return fn(program, type).then(success).catch(error);
  }

  function verify() {
    evalProgramFn(Uzi.compile);
  }

  function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  // HACK(Richo): I can't use the "connecting" flag so I added a new flag
  let waiting = false;
  
  function run() {
    if (Uzi.state.features["interactivity?"]) {
      evalProgramFn(Uzi.run);
    } else {
      if (waiting) return;
      waiting = true;
      connect()
        .then(port => port ? evalProgramFn(Uzi.run) : null)
        .then(() => delay(1000))
        .then(() => disconnect())
        .finally(() => waiting = false);
    }
  }

  function install() {
    if (Uzi.state.features["interactivity?"]) {
      evalProgramFn(Uzi.install);
    } else {
      if (waiting) return;
      waiting = true;
      connect()
        .then(port => port ? evalProgramFn(Uzi.install) : null)
        .then(() => delay(1000))
        .then(() => disconnect())
        .finally(() => waiting = false);
    }
  }

  function toggleInteractive() {
    scheduleAutorun($("#interactive-checkbox").get(0).checked,
                    "TOGGLE INTERACTIVE!");
    saveToLocalStorage();
  }

  function toggleAutosave() {
    saveToLocalStorage();
  }

  function updateUziSyntax() {
    let checked = $("#uzi-syntax-checkbox").get(0).checked;
    UziBlock.setUziSyntax(checked);

    saveToLocalStorage();
  }

  function updateAdvBlocks() {
    let checked = $("#adv-blocks-checkbox").get(0).checked;
    UziBlock.modifyToolbox(checked);
    saveToLocalStorage();
  }

  function updateAllCaps() {
    // if the checkbox has been checked
    if ($("#all-caps-checkbox").get(0).checked) {
      document.body.classList.add("allCapsMode");
      $("button").addClass("allCapsMode");
    }
    // else tickbox has been unmarked
    else {
      document.body.classList.remove("allCapsMode");
      $("button").removeClass("allCapsMode");
    }

    UziBlock.refreshAll();
    saveToLocalStorage();
  }

  function openOptionsDialog() {
    $("#options-modal").modal("show");
  }

  function openInspectorPinDialog() {
    buildPinInspectorDialog();
    $("#inspector-pin-modal").modal("show");
  }

  function openInspectorGlobalDialog() {
    buildGlobalInspectorDialog();
    $("#inspector-global-modal").modal("show");
  }

  function updateConnection (newState, previousState) {
    if (previousState == null
        || (!previousState.connection.isConnected && newState.connection.isConnected)) {
      scheduleAutorun(true, "UPDATE CONNECTION!");
    }
  }

	function scheduleAutorun(forced, origin) {
    if (origin) {
      console.log(origin + " (forced: " + forced + ")");
    }
		let currentTime = +new Date();
		autorunNextTime = currentTime + 250;
    if (forced) {
      dirtyBlocks = dirtyCode = true;
    }
	}

  function success() {
    $("#program-error").hide();
  }

  function error() {
    $("#program-error").show();
  }

	function autorun() {
    if (Uzi.state == undefined) return Promise.resolve();
    if (Uzi.state.debugger.isHalted) return Promise.resolve();
		if (autorunNextTime === undefined) return Promise.resolve();

		let currentTime = +new Date();
		if (currentTime < autorunNextTime) return Promise.resolve();
    autorunNextTime = undefined;

    if (!dirtyBlocks && !dirtyCode) return Promise.resolve();

    let program = null;
    let type = null;

    if (dirtyBlocks) {
	    program = getBlocklyCode();
      type = "json";
    } else if (dirtyCode) {
      program = getTextualCode();
      type = "uzi";
    }

    dirtyBlocks = dirtyCode = false;
    lastProgram = { code: program, type: type };

    // TODO(Richo): This is a mess!
    if (electron &&
        $("#file-name").text() &&
        !$("#file-saved").is(":visible")) {
      $("#file-dirty").show();
    } else {
      $("#file-dirty").hide();
    }

    let connected = Uzi.state.connection.isConnected;
    let interactive = $("#interactive-checkbox").get(0).checked;
    interactive &= Uzi.state.features["interactivity?"];
    let action = connected && interactive ? Uzi.run : Uzi.compile;
    let actionName = action.name.toUpperCase();

    let id = autorunCounter++;
    let beginTime = currentTime;
    console.log(">>> BEGIN " + actionName + ": " + id);
    return action(program, type, true)
      .then(success)
      .catch(error)
      .finally(() => {
        let duration = +new Date() - beginTime;
        console.log(">>> END " + actionName + ": " + id + " (" + duration + " ms)");
      });
	}

  function getBlocklyCode() {
    try {
      let code = UziBlock.getGeneratedCode();
      return JSONX.stringify(code);
    } catch (err) {
      console.log(err);
      return "";
    }
  }

  function getTextualCode() {
    try {
      return UziCode.getProgram();
    } catch (err) {
      console.log(err);
      return "";
    }
  }

  function updateTopBar() {
    if (connecting) return;
    if (Uzi.state.connection.isConnected) {
      $("#connect-button").hide();
      $("#disconnect-button").show();
      $("#disconnect-button").attr("disabled", null);
      $("#port-dropdown").attr("disabled", "disabled");
      $("#run-button").attr("disabled", null);
      $("#more-buttons").attr("disabled", null);
      $("#install-button").attr("disabled", null);
      setSelectedPort(Uzi.state.connection.portName);
    } else {
      $("#disconnect-button").hide();
      $("#connect-button").show();
      $("#connect-button").attr("disabled", null);
      $("#port-dropdown").attr("disabled", null);
      $("#run-button").attr("disabled", "disabled");
      $("#more-buttons").attr("disabled", "disabled");
      $("#install-button").attr("disabled", "disabled");
      updatePortDropdown();
    }

    if (!Uzi.state.features["interactivity?"]) {
      $("#disconnect-button").hide();
      $("#connect-button").hide();
      $("#run-button").attr("disabled", null);
      $("#more-buttons").attr("disabled", null);
      $("#install-button").attr("disabled", null);
    }
      

    if (DEMO) {
      $("#port-dropdown").attr("disabled", "disabled");
      setSelectedPort("simulator");
    }
  }

  function updatePortDropdown() {
    let $ports = $("#port-dropdown");
    let $children = $ports.children();
    for (let i = 0; i < $children.length; i++) {
      if ($children[i].id == "port-dropdown-divider") break;
      $children[i].remove();
    }
    let availablePorts = Uzi.state.connection.availablePorts || [];
    let ports = availablePorts.concat(userPorts.filter(p => availablePorts.indexOf(p) < 0));
    ports.forEach(port => {
      $("<option>")
        .text(port)
        .attr("value", port)
        .insertBefore("#port-dropdown-divider");
    });

    // Make sure we keep the selected port set
    setSelectedPort(selectedPort);
  }

  function updateInspectorPanel(editing) {
    updatePinsPanel(editing);
    updateGlobalsPanel(editing);
    updateTasksPanel();
    updateMemoryPanel();
    updatePseudoVarsPanel();
  }

  function updatePinsPanel(editing) {
    updateValuesPanel(Uzi.state.pins, $("#pins-table tbody"), $("#no-pins-label"), "pin", editing, Uzi.setPinValues);
  }

  function updateGlobalsPanel(editing) {
    updateValuesPanel(Uzi.state.globals, $("#globals-table tbody"), $("#no-globals-label"), "global", editing, Uzi.setGlobalValues);
  }

  function updatePseudoVarsPanel() {
    let pseudoVars = Uzi.state["pseudo-vars"];
    if (pseudoVars.available.length == 0) {
      $("#pseudo-vars-card").hide();
    } else {
      updateValuesPanel(pseudoVars, $("#pseudo-vars-table tbody"), $("#no-pseudo-vars-label"), "pseudo-var");
      $("#pseudo-vars-card").show();
    }
  }

  function updateValuesPanel(values, $container, $emptyLabel, itemPrefix, editing, updateFn) {
    if (!Uzi.state.connection.isConnected) {
      // NOTE(Richo): If we're not connected we simply clear the panel
      values = { available: [], elements: [] };
    }

    let reporting = new Set();
    values.available.forEach(val => {
      if (val.reporting) { reporting.add(val.name); }
    });

    if (reporting.size == 0) {
      $emptyLabel.show();
      $container.html("");
      return;
    } else {
      $emptyLabel.hide();
    }

    function getElementId(val) { return itemPrefix + "-" + val.name; }
    function getEyeId(val) { return getElementId(val) + "-eye"; }

    // NOTE(Richo): The value could have ".", which bothers JQuery but works with document.getElementById
    function getElement(val) { return $(document.getElementById(getElementId(val))); }
    function getEye(val) { return $(document.getElementById(getEyeId(val))); }

    function initializePanel() {
      $container.html("");
      values.available.forEach(val => {
        if (val.reporting) {
          let $row = $("<tr>")
            .append($("<td>")
              .addClass("pl-4")
              .append($("<i>")
                .addClass("fas fa-eye")
                .css("cursor", "pointer")
                .attr("id", getEyeId(val))
                .on("click", () => Plotter.toggle(val.name))))
            .append($("<td>")
              .text(val.name));
          let $value = $("<td>")
              .addClass("text-right")
              .addClass("pr-4")
              .addClass("text-muted")
              .attr("id", getElementId(val))
              .text("?");
          if (editing != null) {            
            $value.attr("contenteditable", "true")
            $value.on("focus", () => {
              editing.add(val.name);
              $value.removeClass("text-muted");
            });
            $value.on("blur", () => editing.delete(val.name));
            $value.on("keydown", function (evt) {
              /*
              NOTE(Richo): If the user presses either Enter (13) or Tab (9) we update the value
              */
              if (13 == evt.keyCode || 9 == evt.keyCode) {
                let new_value = parseFloat(this.innerText);
                if (isFinite(new_value) && updateFn) {
                  try {
                    updateFn([val.name], [new_value]);
                  } catch(err) {
                    console.error(err);
                  }
                }
              }
              /*
              NOTE(Richo): Additionally, if the user presses Enter (13) we prevent the
              default event behavior and blur the input to simulate a "submit" event
              */
              if (13 == evt.keyCode) {
                evt.preventDefault();
                $value.blur();
              }
            });
          }
          $row.append($value);
          $container.append($row);
        }
      });
    };

    if (values.available
        .filter(val => val.reporting)
        .some(val => getElement(val).get(0) == undefined)) {
      // We have new values to add
      initializePanel();
    } else if ($container.children().length > values.available.length) {
      // We have old values to remove
      initializePanel();
    }

    values.elements.forEach(val => {
      let $eye = getEye(val);
      $eye.css("color", Plotter.colorFor(val.name) || "white");

      if (reporting.has(val.name)) {
        // Ignore this element if we're editing its value
        if (editing && editing.has(val.name)) return;
        
        let $item = getElement(val);
        if ($item.get(0) == undefined) { initializePanel(); }

        let old = $item.data("old-value");
        let cur = val.value;
        if (cur != null && cur != old && Uzi.state.connection.isConnected) {
          $item.data("old-value", cur);
          $item.data("last-update", +new Date());
          $item.removeClass("text-muted");
        } else {
          let lastUpdate = $item.data("last-update") || 0;
          let now = +new Date();
          if (now - lastUpdate > 2500) {
            $item.addClass("text-muted");
          }
        }

        if (cur != null) {
          if (cur == Infinity) {
            $item.text("∞");
          } else if (cur == -Infinity) {
            $item.text("-∞");
          } else if (isNaN(cur)) {
            $item.text("NaN");
          } else {
            $item.text(cur.toFixed(2));
          }
        } else {
          $item.text("?");
        }
      }
    });

    values.available.forEach(val => {
      if (!reporting.has(val.name)) {
        let $item = getElement(val);
        if ($item != undefined) { $item.parent().remove(); }
      }
    });
  }

  function updateTasksPanel() {
    // TODO(Richo): Update in place, don't clear and recreate.
    $("#tasks-table tbody").html("");
    if (!Uzi.state.connection.isConnected) return;

    for (let i = 0; i < Uzi.state.tasks.length; i++) {
      let task = Uzi.state.tasks[i];
      let css = "text-muted";
      let html = "";
      if (task.isError) {
        css = "text-warning";
        html = '<i class="fas fa-skull-crossbones mr-2"></i>error';
      } else if (task.isRunning) {
        css = "text-success";
        html = '<i class="fas fa-running mr-2"></i>running';
      } else {
        css = "text-danger";
        html = '<i class="fas fa-hand-paper mr-2"></i>stopped';
      }
      $("#tasks-table tbody")
        .append($("<tr>")
          .append($("<td>")
            .addClass("pl-4")
            .text(task.scriptName))
          .append($("<td>")
            .addClass(css)
            .html(html)));
    }
  }

  function updateMemoryPanel() {
    if (Uzi.state.connection.isConnected) {
      $("#arduino-memory").text(Uzi.state.memory.arduino || "?");
      $("#uzi-memory").text(Uzi.state.memory.uzi || "?");
    } else {
      $("#arduino-memory").text("?");
      $("#uzi-memory").text("?");
    }
  }

  function buildPinInspectorDialog() {
    let container = $("#inspector-pin-modal-container");
    container.html("");

    let ncols = 6;
    let row;

    function buildInput (pin, index) {
      if (index % ncols == 0) {
        row = $("<div>").addClass("row");
        container.append(row);
      }
      let id = pin.name + "-checkbox";
      let input = $("<input>")
        .attr("type", "checkbox")
        .attr("id", id)
        .attr("name", "pins-checkbox")
        .attr("value", pin.name)
        .addClass("custom-control-input");
      input.get(0).checked = pin.reporting;
      input.on("change", function () {
        let reportEnabled = this.checked;
        Uzi.setPinReport([pin.name], [reportEnabled]);
      });

      row.append($("<div>")
        .addClass("col-" + (12 / ncols))
        .append($("<div>")
          .addClass("custom-control")
          .addClass("custom-checkbox")
          .addClass("custom-control-inline")
          .append(input)
          .append($("<label>")
            .addClass("custom-control-label")
            .attr("for", id)
            .text(pin.name))));
    }

    // Digital pins
    container.append($("<h6>").text("Digital:"));
    Uzi.state.pins.available
      .filter(pin => pin.name.startsWith("D"))
      .forEach(buildInput);

    // Analog pins
    container.append($("<h6>").addClass("mt-4").text("Analog:"));
    Uzi.state.pins.available
      .filter(pin => pin.name.startsWith("A"))
      .forEach(buildInput);
  }

  function buildGlobalInspectorDialog() {
    let container = $("#inspector-global-modal-container");
    container.html("");

    let ncols = 6;
    let row;

    function buildInput (global, index) {
      if (index % ncols == 0) {
        row = $("<div>").addClass("row");
        container.append(row);
      }
      let id = "global-" + global.name + "-checkbox";
      let input = $("<input>")
        .attr("type", "checkbox")
        .attr("id", id)
        .attr("name", "globals-checkbox")
        .attr("value", global.name)
        .addClass("custom-control-input");
      input.get(0).checked = global.reporting;
      input.on("change", function () {
        let reportEnabled = this.checked;
        Uzi.setGlobalReport([global.name], [reportEnabled]);
      });

      row.append($("<div>")
        .addClass("col-" + (12 / ncols))
        .append($("<div>")
          .addClass("custom-control")
          .addClass("custom-checkbox")
          .addClass("custom-control-inline")
          .append(input)
          .append($("<label>")
            .addClass("custom-control-label")
            .attr("for", id)
            .text(global.name))));
    }

    container.append($("<h6>").text("Global:"));
    let globals = Uzi.state.globals.available;
    if (globals.length == 0) {
      container.append($("<i>").text("* No globals found *"));
    } else {
      Uzi.state.globals.available.forEach(buildInput);
    }
  }

  return IDE;
})();
