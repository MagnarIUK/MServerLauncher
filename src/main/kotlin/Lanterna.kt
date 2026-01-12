package com.magnariuk

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Button
import com.googlecode.lanterna.gui2.CheckBox
import com.googlecode.lanterna.gui2.ComboBox
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.GridLayout
import com.googlecode.lanterna.gui2.InputFilter
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.ProgressBar
import com.googlecode.lanterna.gui2.TextBox
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowBasedTextGUI
import com.googlecode.lanterna.gui2.dialogs.MessageDialog
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.magnariuk.data.configs.Backup
import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.Network
import com.magnariuk.util.api.checkMinecraftVersion
import com.magnariuk.util.api.getVersions
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.backupApi.generateUniqueHex
import com.magnariuk.util.instance.checkInstance
import com.magnariuk.util.instance.configsApi.attachResourcePack
import com.magnariuk.util.instance.createInstance
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.instance.launchServer
import com.magnariuk.util.instance.listInstances
import com.magnariuk.util.instance.openInstanceFolder
import com.magnariuk.util.openInDefaultEditor
import com.magnariuk.util.t
import kotlinx.coroutines.runBlocking
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.LinkedList
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

fun launchLanterna(instance: String? = null){
    val terminal = DefaultTerminalFactory().createTerminal()
    val screen = TerminalScreen(terminal)
    screen.startScreen()

    val gui = MultiWindowTextGUI(screen)

    val mainWindow = BasicWindow("Instance Manager")
    mainWindow.setHints(listOf(Window.Hint.CENTERED))
    val mainPanel = Panel()
    mainPanel.addComponent(Label(t("tui.selectInstance")))


    mainPanel.addComponent(Label("Search:"))
    val searchBox = TextBox()
    mainPanel.addComponent(searchBox)

    mainPanel.addComponent(Label("------------------"))

    val instanceList = ActionListBox(TerminalSize(30, 10))

    var cachedInstances: List<Pair<String, INSTANCE_CONFIG>> = emptyList()

    fun reloadData() {
        cachedInstances = listInstances(apiMode = true)
    }

    fun refreshList(filter: String) {
        instanceList.clearItems()

        instanceList.addItem("New Instance") {
            val versions = showLoadingScreen(gui, "Fetching Minecraft versions...") {
                runBlocking { getVersions(Network()).getList() }
            }

            if (versions != null) {
                showCreateInstance(gui, versions)

                reloadData()
                refreshList("")
                searchBox.text = ""
            }
        }

        val matches = cachedInstances.filter {
            it.first.contains(filter, ignoreCase = true)
        }

        matches.forEach { instance ->
            instanceList.addItem(instance.first) {
                showInstance(gui, instance)
                refreshList("")
                searchBox.text = ""
            }
        }

        if (matches.isEmpty() && filter.isNotEmpty()) {
            instanceList.addItem("No results found..."){}
        }
    }

    searchBox.setTextChangeListener { newText, _ ->
        refreshList(newText)
    }

    reloadData()
    refreshList("")


    mainPanel.addComponent(instanceList)
    mainPanel.addComponent(Button("Exit"){ mainWindow.close() })
    mainWindow.component = mainPanel
    gui.addWindowAndWait(mainWindow)
}
fun <T> showLoadingScreen(
    gui: WindowBasedTextGUI,
    message: String,
    task: () -> T
): T? {
    val window = BasicWindow("Please Wait")
    window.setHints(listOf(Window.Hint.CENTERED))

    val panel = Panel(GridLayout(1))
    panel.addComponent(Label(message))
    panel.addComponent(Label("Processing..."))
    window.component = panel

    var result: T? = null
    var error: Exception? = null

    thread {
        try {
            result = task()
        } catch (e: Exception) {
            error = e
        } finally {
            gui.guiThread.invokeLater {
                window.close()
            }
        }
    }

    gui.addWindowAndWait(window)

    if (error != null) {
        showMessageDialog(gui, "Error", "Operation failed:\n${error!!.message}")
        return null
    }

    return result
}

fun showCreateInstance(gui: WindowBasedTextGUI, versions: List<String>) {
    val window = BasicWindow("Create New Instance")

    val panel = Panel(GridLayout(2))
    val nameBox = panel.addInput("Instance Name")

    val versionBox = panel.addSearchableInput(gui,"Minecraft Version: ", versions)

    val memoryBox = panel.addInput("Amount of Memory", "4G")

    val loaderBox = panel.addChoice("Loader: ", listOf("vanilla", "fabric"))
    panel.spacer()
    val checkboxBox = panel.addCheckbox("Create all files")

    panel.spacer()



    panel.addButtons(

        "Create" to {

            val doesExist = checkInstance(nameBox.text)
            if (!doesExist) {
                if(nameBox.text.isNotBlank() && versionBox.text.isNotBlank() && memoryBox.text.isNotBlank() && loaderBox.text.isNotBlank()) {
                    if(checkMinecraftVersion(versionBox.text, apiMode = true)){
                        createInstance(
                            name = nameBox.text,
                            version = versionBox.text,
                            memory = memoryBox.text,
                            autoBackup = false,
                            resourcePack = "",
                            loader = loaderBox.text,
                            loaderVersion = "latest",
                            apiMode = true,
                            runServer = checkboxBox.isChecked
                        )
                        window.close()
                    } else{
                        showMessageDialog(gui, "Error Creating Instance", t("util.api.versionDoesNotExist", versionBox.text, "Non valid version"))
                    }
                } else{
                    showMessageDialog(gui, "Error Creating Instance", "Fields cannot be empty")
                }
            } else{
                showMessageDialog(gui, "Error Creating Instance", t("argument.errors.instanceAlreadyExists", nameBox.text))
            }

        },
        "Cancel" to {window.close()},
    )

    window.component = panel
    gui.addWindowAndWait(window)
}

fun showEditInstance(
    gui: WindowBasedTextGUI,
    instanceName: String,
    config: INSTANCE_CONFIG,
    onSuccess: () -> Unit
) {
    val window = BasicWindow("Edit Instance: $instanceName")
    val panel = Panel(GridLayout(2))

    val memoryBox = panel.addInput("Memory:", config.memory)

    val allVersions = runBlocking { getVersions(Network()).getList() }
    val versionBox = panel.addSearchableInput(gui, "Minecraft Version:", allVersions, config.version.minecraft)

    val loaders = listOf("vanilla", "fabric")
    val loaderBox = panel.addChoice("Loader:", loaders)

    if (loaders.contains(config.version.loader.type)) {
        loaderBox.selectedItem = config.version.loader.type
    }
    panel.spacer()
    val backupCheck = panel.addCheckbox("Enable Auto-Backup")
    backupCheck.isChecked = config.autoBackup

    panel.spacer()

    panel.addButtons(
        "Save" to {
            try {
                editInstance(
                    name = instanceName,
                    version = versionBox.text,
                    memory = memoryBox.text,
                    autoBackup = backupCheck.isChecked,
                    loaderType = loaderBox.selectedItem,
                    apiMode = true
                )

                showMessageDialog(gui, "Success", "Configuration updated!")
                window.close()

                onSuccess()

            } catch (e: Exception) {
                showMessageDialog(gui, "Error", "Failed to save config: ${e.message}")
            }
        },
        "Cancel" to { window.close() }
    )

    window.component = panel
    gui.addWindowAndWait(window)
}

fun showInstance(gui: WindowBasedTextGUI, instance: Pair<String, INSTANCE_CONFIG>) {
    val instanceName = instance.first
    var instanceConfig = instance.second
    val subWindow = BasicWindow("Instance: $instanceName")

    subWindow.setHints(listOf(Window.Hint.CENTERED))

    val panel = Panel()

    var verLabel = Label("Version: ${instanceConfig.version.minecraft}")
    var memLabel = Label("Memory: ${instanceConfig.memory}")
    var loaderLabel = Label("Loader: ${instanceConfig.version.loader.type}")

    panel.addComponent(verLabel)
    panel.addComponent(memLabel)
    panel.addComponent(loaderLabel)
    panel.addComponent(Label(" "))
    panel.spacer()

    panel.addButton("Launch"){
        showServerConsole(gui, instanceName)
    }

    panel.addButton("Edit"){
        showEditInstance(gui, instanceName, instanceConfig) {

            val newConfig = getInstance(instanceName)!!

            instanceConfig = newConfig

            verLabel.text = "Version: ${newConfig.version.minecraft}"
            memLabel.text = "Memory: ${newConfig.memory}"
            loaderLabel.text = "Loader: ${newConfig.version.loader.type}"
        }
    }
    panel.addButton("Backup"){
        showBackupMenu(gui, instanceName)
    }
    panel.addButton("World"){
        showWorldMenu(gui, instanceName)
    }
    panel.addButton("Open"){
        openInstanceFolder(instanceName, apiMode = true)
    }
    panel.addButton("Server Properties"){
        val file = Path.of(readConfig().instancesFolder, instanceName, "server.properties")
        openInDefaultEditor(file.toFile(), t("command.sp.fileNotExists", instanceName, ),apiMode = true)
    }
    panel.addButton("Add Resourcepack"){
        showAttachResourcePackDialog(gui, instanceName)
    }
    panel.addButton("Modrinth (WIP)"){}
    panel.addButton("Delete"){
        val result = MessageDialog.showMessageDialog(
            gui,
            "Confirm Deletion",
            "Are you sure you want to permanently delete '$instanceName'?",
            MessageDialogButton.Yes,
            MessageDialogButton.No
        )

        if (result == MessageDialogButton.Yes) {
            val cfg = readConfig()
            val instancesPath = Path.of(cfg.instancesFolder)
            val instancePath = instancesPath.resolve(instanceName).toFile()

            deleteDirectoryWithProgress(
                gui,
                title = "Deleting $instanceName instance",
                folder = instancePath,
            ) {
                MessageDialog.showMessageDialog(gui, "Success", "Instance deleted successfully.")
            }

            subWindow.close()
        }
    }
    panel.spacer()

    panel.addButton("Back") {
        subWindow.close()
    }

    subWindow.component= panel
    gui.addWindowAndWait(subWindow)

}




fun showServerConsole(gui: WindowBasedTextGUI, instanceName: String) {
    val window = BasicWindow("Console: $instanceName")
    window.setHints(listOf(Window.Hint.CENTERED, Window.Hint.EXPANDED))


    val mainPanel = Panel(BorderLayout())

    val logBox = TextBox()
    logBox.isReadOnly = true
    logBox.layoutData = BorderLayout.Location.CENTER
    mainPanel.addComponent(logBox)

    val logBuffer = LinkedList<String>()
    val maxLogLines = 100

    fun appendLog(msg: String) {
        gui.guiThread.invokeLater {
            val currentScrollRow = logBox.caretPosition.row
            val totalLinesBefore = logBox.lineCount

            val wasAtBottom = totalLinesBefore == 0 || currentScrollRow >= totalLinesBefore - 1

            if (logBuffer.size >= maxLogLines) {
                logBuffer.removeFirst()
            }
            logBuffer.add(msg)

            logBox.text = logBuffer.joinToString("\n")

            val totalLinesAfter = logBox.lineCount

            if (wasAtBottom) {
                logBox.setCaretPosition(totalLinesAfter - 1, 0)
            } else {
                val newRow = currentScrollRow.coerceAtMost(totalLinesAfter - 1)
                logBox.setCaretPosition(newRow, 0)
            }
        }
    }

    val inputPanel = Panel(LinearLayout(Direction.HORIZONTAL))
    inputPanel.layoutData = BorderLayout.Location.BOTTOM
    inputPanel.addComponent(Label("> "))

    val commandInput = TextBox()
    commandInput.layoutData = LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)

    var activeProcess: Process? = null
    var serverWriter: BufferedWriter? = null

    val sendButton = Button("Send") {
        val cmd = commandInput.text
        if (cmd.isNotBlank() && serverWriter != null) {
            try {
                serverWriter!!.write(cmd + "\n")
                serverWriter!!.flush()
                appendLog("> $cmd")
                commandInput.text = ""
            } catch (e: Exception) {
                appendLog("[GUI Error] Failed to send: ${e.message}")
            }
        }
    }

    inputPanel.addComponent(commandInput)
    inputPanel.addComponent(sendButton)

    inputPanel.addComponent(Button("STOP") {
        serverWriter?.write("stop\n")
        serverWriter?.flush()

    })

    inputPanel.addComponent(Button("Exit") {
        if (activeProcess != null && activeProcess!!.isAlive) {
            val result = MessageDialog.showMessageDialog(gui, "Confirm Exit",
                "Server is currently running.\nDo you want to stop it before exiting?",
                MessageDialogButton.Yes, MessageDialogButton.No)

            if (result == MessageDialogButton.Yes) {
                try {
                    serverWriter?.write("stop\n")
                    serverWriter?.flush()
                    serverWriter?.close()
                } catch (e: Exception) {}

                val procToKill = activeProcess
                thread(start = true) {
                    val exitedGracefully = procToKill?.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)

                    exitedGracefully?.let {
                        if (!it) {
                            procToKill.destroy()
                        }
                    }
                }

                window.close()
            } else {
                window.close()
            }
        } else {
            window.close()
        }
    })

    mainPanel.addComponent(inputPanel)

    window.component = mainPanel
    window.focusedInteractable = commandInput

    thread(start = true) {
        appendLog("Preparing to launch $instanceName...")
        runBlocking {
            launchServer(
                instanceName,
                exitImmediately = false,
                logger = { msg -> appendLog(msg) },
                onProcessStart = { process ->
                    activeProcess = process
                    serverWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
                },
                apiMode = true
            )
        }
        appendLog("Server process terminated.")
    }

    gui.addWindowAndWait(window)
}

fun showAttachResourcePackDialog(gui: WindowBasedTextGUI, instanceName: String) {
    val window = BasicWindow("Attach Resource Pack")
    window.setHints(listOf(Window.Hint.CENTERED))

    val panel = Panel(GridLayout(2))

    panel.addHeader("Resource Pack Config")

    val urlBox = panel.addInput("Direct Download URL:")
    urlBox.preferredSize = TerminalSize(40, 1)
    panel.spacer()

    panel.addButtons(
        "Attach" to {
            val inputUrl = urlBox.text.trim()


            if (inputUrl.isBlank() || !(inputUrl.startsWith("http://") || inputUrl.startsWith("https://"))) {
                showMessageDialog(gui, "Invalid URL", "You must provide a valid direct download link.\n(Starting with http:// or https://)")
            } else{
                try {
                    val success = attachResourcePack(instanceName, inputUrl)

                    if (success) {
                        showMessageDialog(gui, "Success", "Resource Pack URL attached successfully!")
                        window.close()
                    } else {
                        showMessageDialog(gui, "Error", "Failed to update config.\nCheck console for details.")
                    }
                } catch (e: Exception) {
                    showMessageDialog(gui, "Exception", "Error: ${e.message}")
                }
            }

        },
        "Cancel" to { window.close() }
    )

    window.component = panel
    gui.addWindowAndWait(window)
}

fun showBackupMenu(gui: WindowBasedTextGUI, instanceName: String) {
    val window = BasicWindow("Backups: $instanceName")
    window.setHints(listOf(Window.Hint.CENTERED))
    val panel = Panel()

    panel.addComponent(Label("Search/Filter:"))
    val searchBox = TextBox()
    panel.addComponent(searchBox)
    panel.addComponent(Label("----------------"))

    val list = ActionListBox(TerminalSize(40, 10))

    fun refreshList() {
        list.clearItems()
        val config = getInstance(instanceName) ?: return
        val filter = searchBox.text.lowercase()

        val sortedBackups = config.backups.entries.sortedByDescending { it.value.dateTime }

        sortedBackups.forEach { (id, info) ->
            val displayText = "$id [${info.dateTime}]"
            if (displayText.lowercase().contains(filter) || info.desc.lowercase().contains(filter)) {

                list.addItem(displayText) {
                    showBackupDetails(gui, instanceName, id, info) {
                        refreshList()
                    }
                }
            }
        }
    }

    searchBox.setTextChangeListener { _, _ -> refreshList() }

    panel.addComponent(list)
    panel.addComponent(Label(" "))

    panel.addButtons(
        "Create New" to {
            performCreateBackup(gui, instanceName) { refreshList() }
        },
        "Delete ALL" to {
            val res = MessageDialog.showMessageDialog(gui, "DANGER", "Delete ALL backups?", MessageDialogButton.Yes, MessageDialogButton.No)
            if (res == MessageDialogButton.Yes) {
                val cfg = readConfig()
                val backupFolder = Path.of(cfg.instancesFolder, instanceName, "backups").toFile()
                deleteDirectoryWithProgress(gui, "Deleting all backups...", backupFolder) {
                    editInstance(instanceName, backups = emptyMap(), apiMode = true)
                    refreshList()
                    showMessageDialog(gui, "Done", "All backups deleted.")
                }
            }
        },
        "Back" to { window.close() }
    )

    refreshList()
    window.component = panel
    gui.addWindowAndWait(window)
}

fun showBackupDetails(gui: WindowBasedTextGUI, instanceName: String, id: String, info: Backup, onUpdate: () -> Unit) {
    val window = BasicWindow("Backup: $id")
    val panel = Panel(GridLayout(2))

    panel.addHeader("Details")
    panel.addLabel("Date:"); panel.addLabel(info.dateTime)
    panel.addLabel("Ver:"); panel.addLabel(info.version)

    panel.addLabel("Desc:")

    val descBox = TextBox(info.desc)
    descBox.isReadOnly = true
    descBox.preferredSize = TerminalSize(35, 4)
    panel.addComponent(descBox)

    panel.addComponent(
        Label(" "),
        GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false, 2, 1)
    )

    val buttonPanel = Panel(LinearLayout(Direction.HORIZONTAL))

    val rollbackBtn = Button("Rollback") {
        performRollback(gui, instanceName, id) { window.close() }
    }

    val deleteBtn = Button("Delete") {
        val res = MessageDialog.showMessageDialog(gui, "Confirm", "Delete this backup?", MessageDialogButton.Yes, MessageDialogButton.No)
        if (res == MessageDialogButton.Yes) {
            val cfg = readConfig()
            val ts = info.dateTime.replace(".", "").replace(":", "")
            val file = Path.of(cfg.instancesFolder, instanceName, "backups", "$ts-world-backup.zip").toFile()
            if (file.exists()) file.delete()

            val inst = getInstance(instanceName)!!
            val newMap = inst.backups.toMutableMap()
            newMap.remove(id)
            editInstance(instanceName, backups = newMap, apiMode = true)

            window.close()
            onUpdate()
        }
    }

    val backBtn = Button("Back") { window.close() }

    buttonPanel.addComponent(rollbackBtn)
    buttonPanel.addComponent(deleteBtn)
    buttonPanel.addComponent(backBtn)

    panel.addComponent(
        buttonPanel,
        GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, true, false, 2, 1)
    )

    descBox.inputFilter = InputFilter { _, key ->
        if (key.keyType == KeyType.ArrowDown) {
            window.focusedInteractable = rollbackBtn
            false
        } else {
            true
        }
    }

    val upFilter = InputFilter { _, key ->
        if (key.keyType == KeyType.ArrowUp) {
            window.focusedInteractable = descBox
            false
        } else true
    }
    rollbackBtn.inputFilter = upFilter
    deleteBtn.inputFilter = upFilter
    backBtn.inputFilter = upFilter

    window.component = panel
    gui.addWindowAndWait(window)
}

fun showWorldMenu(gui: WindowBasedTextGUI, instanceName: String) {
    val window = BasicWindow("World Management: $instanceName")
    window.setHints(listOf(Window.Hint.CENTERED))

    val panel = Panel(GridLayout(1))


    panel.addButton("Reset World (Delete)") {
        val result = MessageDialog.showMessageDialog(
            gui,
            "Confirm Reset",
            "Are you sure? This will PERMANENTLY delete the 'world' folder.\nThis cannot be undone.",
            MessageDialogButton.Yes,
            MessageDialogButton.No
        )

        if (result == MessageDialogButton.Yes) {
            val config = readConfig()
            val worldFolder = Path.of(config.instancesFolder, instanceName, "world").toFile()

            if (worldFolder.exists()) {
                deleteDirectoryWithProgress(gui, "Resetting World...", worldFolder) {
                    MessageDialog.showMessageDialog(gui, "Success", "World has been reset.")
                }
            } else {
                MessageDialog.showMessageDialog(gui, "Error", "No world folder found to delete.")
            }
        }
    }

    panel.addComponent(Label(" "))
    panel.addButton("Back") { window.close() }

    window.component = panel
    gui.addWindowAndWait(window)
}
fun deleteDirectoryWithProgress(
    gui: WindowBasedTextGUI,
    title: String,
    folder: File,
    onSuccess: () -> Unit = {}
) {
    val window = BasicWindow(title)
    window.setHints(listOf(Window.Hint.CENTERED))

    val panel = Panel(GridLayout(1))
    panel.addComponent(Label("Scanning files..."))

    val pBar = ProgressBar(0, 100)
    pBar.preferredWidth = 40
    panel.addComponent(pBar)

    val statusLabel = Label("Please wait...")
    panel.addComponent(statusLabel)

    window.component = panel

    thread {
        try {

            val allFiles = folder.walkBottomUp().toList()
            val total = allFiles.size

            gui.guiThread.invokeLater {
                pBar.max = total
                pBar.value = 0
            }

            allFiles.forEachIndexed { index, file ->
                file.delete()

                gui.guiThread.invokeLater {
                    pBar.value = index + 1
                    statusLabel.text = "Removing: ${file.name.take(30)}"
                }

            }

            gui.guiThread.invokeLater {
                window.close()
                onSuccess()
            }
        } catch (e: Exception) {
            gui.guiThread.invokeLater {
                window.close()
                MessageDialog.showMessageDialog(gui, "Error", "Failed to delete: ${e.message}")
            }
        }
    }
    gui.addWindowAndWait(window)
}

fun Panel.addHeader(text: String) {
    this.addComponent(
        Label(text).setForegroundColor(TextColor.ANSI.YELLOW),
        GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false, 2, 1)
    )
    this.addComponent(
        Label("----------------"),
        GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false, 2, 1)
    )
}
fun Panel.addInput(label: String, defaultText: String = ""): TextBox {
    this.addComponent(Label(label))

    val textBox = TextBox(defaultText)
        .setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER))

    this.addComponent(textBox)
    return textBox
}
fun Panel.addLabel(label: String) {
    this.addComponent(Label(label))
}
fun Panel.spacer() {
    this.addComponent(Label(" "))
}
fun Panel.addButton(label: String, action: () -> Unit) {
    this.addComponent(Button(label, action))
}
fun Panel.addButtons(vararg buttons: Pair<String, () -> Unit>) {
    val buttonPanel = Panel(LinearLayout(Direction.HORIZONTAL))

    for ((label, action) in buttons) {
        buttonPanel.addComponent(Button(label, action))
    }

    this.addComponent(
        buttonPanel,
        GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, true, false, 2, 1)
    )
}
fun Panel.addChoice(label: String, options: List<String>): ComboBox<String> {
    this.addComponent(Label(label))

    val comboBox = ComboBox(options)
        .setReadOnly(true)
        .setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER))

    this.addComponent(comboBox)
    return comboBox
}
fun Panel.addComboInput(label: String, suggestions: List<String>): ComboBox<String> {
    this.addComponent(Label(label))

    val comboBox = ComboBox(suggestions)
        .setReadOnly(false)
        .setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER))

    this.addComponent(comboBox)
    return comboBox
}
fun showSearchPopup(gui: WindowBasedTextGUI, title: String, items: List<String>): String? {
    var selectedItem: String? = null

    val window = BasicWindow(title)
    val panel = Panel(GridLayout(1))

    panel.addComponent(Label("Type to filter:"))
    val filterBox = TextBox()
    panel.addComponent(filterBox)

    val listBox = ActionListBox(TerminalSize(30, 10))
    panel.addComponent(listBox)

    fun refreshList(filter: String) {
        listBox.clearItems()
        val matches = items.filter { it.contains(filter, ignoreCase = true) }.take(20)

        matches.forEach { item ->
            listBox.addItem(item) {
                selectedItem = item
                window.close()
            }
        }

        if (matches.isEmpty()) listBox.addItem("No results...") {}
    }

    filterBox.setTextChangeListener { newText, _ ->
        refreshList(newText)
    }

    panel.addComponent(Button("Cancel") { window.close() })

    refreshList("")

    window.component = panel
    gui.addWindowAndWait(window)

    return selectedItem
}

fun Panel.addCheckbox(label: String): CheckBox {
    val cb = CheckBox(label)
    this.addComponent(cb)
    return cb
}
fun showMessageDialog(gui: WindowBasedTextGUI, title: String, message: String) {
    MessageDialog.showMessageDialog(gui, title, message)
}

fun Panel.addSearchableInput(
    gui: WindowBasedTextGUI,
    label: String,
    allOptions: List<String>,
    default: String = ""
): TextBox {
    this.addComponent(Label(label))

    val subPanel = Panel(LinearLayout(Direction.HORIZONTAL))

    val textBox = TextBox(default).setLayoutData(
        LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)
    )
    textBox.layoutData = GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER)

    val searchBtn = Button("Search") {
        val result = showSearchPopup(gui, "Select Version", allOptions)
        if (result != null) {
            textBox.text = result
        }
    }

    subPanel.addComponent(textBox)
    subPanel.addComponent(searchBtn)

    this.addComponent(subPanel)
    return textBox
}
fun performCreateBackup(gui: WindowBasedTextGUI, instanceName: String, onComplete: () -> Unit) {
    val window = BasicWindow("New Backup")
    val panel = Panel(GridLayout(2))
    val descBox = panel.addInput("Description (Optional):")

    panel.addButtons(
        "Start Backup" to {
            window.close()

            val cfg = readConfig()
            val instancePath = Path.of(cfg.instancesFolder, instanceName)
            val worldFolder = instancePath.resolve("world").toFile()

            if (!worldFolder.exists()) {
                showMessageDialog(gui, "Error", "World folder does not exist!")
                return@to
            }

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HH:mm:ss"))
            val instanceCfg = getInstance(instanceName)!!
            val backupId = generateUniqueHex(instanceCfg.backups.keys)
            val fileName = "${timestamp.replace(".", "").replace(":", "")}-world-backup.zip"
            val destFile = instancePath.resolve("backups").resolve(fileName).toFile()
            destFile.parentFile.mkdirs()

            zipWithProgressTUI(gui, worldFolder, destFile) {
                val newBackup = Backup(
                    version = instanceCfg.version.minecraft,
                    dateTime = timestamp,
                    desc = descBox.text
                )
                val newBackups = instanceCfg.backups.toMutableMap()
                newBackups[backupId] = newBackup
                editInstance(instanceName, backups = newBackups, apiMode = true)

                showMessageDialog(gui, "Success", "Backup Created!")
                onComplete()
            }
        },
        "Cancel" to { window.close() }
    )
    window.component = panel
    gui.addWindowAndWait(window)
}

fun performRollback(gui: WindowBasedTextGUI, instanceName: String, backupId: String, onComplete: () -> Unit) {
    val result = MessageDialog.showMessageDialog(gui, "Confirm Rollback",
        "Are you sure?\nCurrent world will be DELETED and replaced.", MessageDialogButton.Yes, MessageDialogButton.No)

    if (result == MessageDialogButton.Yes) {
        val cfg = readConfig()
        val instancePath = Path.of(cfg.instancesFolder, instanceName)
        val instanceCfg = getInstance(instanceName)!!
        val backupInfo = instanceCfg.backups[backupId]!!

        val ts = backupInfo.dateTime.replace(".", "").replace(":", "")
        val zipFile = instancePath.resolve("backups").resolve("$ts-world-backup.zip").toFile()
        val worldFolder = instancePath.resolve("world").toFile()

        if (!zipFile.exists()) {
            showMessageDialog(gui, "Error", "Backup file missing from disk!")
            return
        }

        if (worldFolder.exists()) {
            deleteDirectoryWithProgress(gui, "Deleting current world...", worldFolder) {}
        }

        unzipWithProgressTUI(gui, zipFile, worldFolder) {
            showMessageDialog(gui, "Success", "World rolled back to $backupId")
            onComplete()
        }
    }
}

fun zipWithProgressTUI(gui: WindowBasedTextGUI, sourceFolder: File, zipFile: File, onSuccess: () -> Unit) {
    val window = BasicWindow("Creating Backup...")
    window.setHints(listOf(Window.Hint.CENTERED))
    val pBar = ProgressBar(0, 100); pBar.preferredWidth = 40
    val label = Label("Preparing...")
    val panel = Panel(GridLayout(1)); panel.addComponent(label); panel.addComponent(pBar)
    window.component = panel

    thread {
        try {
            val allFiles = sourceFolder.walkBottomUp().filter { it.isFile }.toList()

            gui.guiThread.invokeLater { pBar.max = allFiles.size }

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                allFiles.forEachIndexed { index, file ->
                    val entryName = sourceFolder.toPath().relativize(file.toPath()).toString().replace("\\", "/")
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()

                    gui.guiThread.invokeLater {
                        pBar.value = index + 1
                        label.text = "Zipping: ${file.name.take(20)}"
                    }
                }
            }
            gui.guiThread.invokeLater {
                window.close()
                onSuccess()
            }
        } catch (e: Exception) {
            gui.guiThread.invokeLater {
                window.close()
                showMessageDialog(gui, "Error", "Backup failed: ${e.message}")
            }
        }
    }

    gui.addWindowAndWait(window)
}

fun unzipWithProgressTUI(gui: WindowBasedTextGUI, zipFile: File, destFolder: File, onSuccess: () -> Unit) {
    val window = BasicWindow("Restoring Backup...")
    window.setHints(listOf(Window.Hint.CENTERED))
    val pBar = ProgressBar(0, 100); pBar.preferredWidth = 40
    val label = Label("Reading Zip...")
    val panel = Panel(GridLayout(1)); panel.addComponent(label); panel.addComponent(pBar)
    window.component = panel

    thread {
        try {

            val allFiles = zipFile.walkBottomUp().filter { it.isFile }.toList()
            gui.guiThread.invokeLater { pBar.max = allFiles.size }
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().toList()

                entries.forEachIndexed { index, entry ->
                    val outFile = destFolder.resolve(entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output -> input.copyTo(output) }
                        }
                    }
                    gui.guiThread.invokeLater {
                        pBar.value = index + 1
                        label.text = "Restoring: ${entry.name.take(20)}"
                    }
                }
            }
            gui.guiThread.invokeLater { window.close(); onSuccess() }
        } catch (e: Exception) {
            gui.guiThread.invokeLater { window.close(); showMessageDialog(gui, "Error", "Rollback failed: ${e.message}") }
        }
    }
    gui.waitForWindowToClose(window)
}