# clyze-intellij-plugin

<!-- Plugin description -->
This plugin integrates the Clyze Analysis Workbench with IDEs based on the IntelliJ Platform.
<!-- Plugin description end -->

## Installation

Build the plugin (`../gradlew buildPlugin`) and install it manually using
<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## IDE Test

To test the plugin in a new IDE instance, run:

```
../gradlew runIde
```



## Use

The plugin has a dedicated "Clyze" tool window with three panes:

* **Main:**

  * To connect to the server, click <kbd>Sync With Server</kbd> and the code structure tree will be updated (requires setup via <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Clyze</kbd>). If the server contains a project with the same name as the current project, that node will be selected in the tree.

  * To post the current project as a code snapshot, click <kbd>Post Code Snapshot</kbd>.

  * To run an analysis, select a code snapshot in the code structure tree and click <kbd>Analyze...</kbd>. Depending on the analysis selected, a dialog will pop up to configure the analysis to run.

* **Line Results:** To look up the analysis results for a given source line, right-click on that line and select <kbd>Look Up Line With Clyze</kbd>.
The analysis results will appear in the this pane.

* **Analysis Outputs:** Shows the results of a finished analysis for a selected project/snapshot/analysis path in the code structure tree (Main pane).

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
