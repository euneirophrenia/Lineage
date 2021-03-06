package GUI


import java.awt.{BorderLayout, Color, Font, Image}
import java.awt.event.ActionEvent

import SQL.{Dialect, OracleDialect, SQLServerDialect}
import javax.swing.{JMenu, JMenuBar, JMenuItem, SwingUtilities, _}
import org.graphstream.graph.implementations._
import org.graphstream.ui.swingViewer._
import org.graphstream.ui.view._

object DataLineage  extends App {

	private val CSS_PATH = System.getProperty("user.dir") + "/Resources/graph.css"
	private val LOGO_PATH = System.getProperty("user.dir") + "/Resources/logo.png"
	private val controller = new Controller

	val dialects = Map[String, Dialect](("SQL Server" -> new SQLServerDialect), ("Oracle" -> new OracleDialect))

	var graphMap: collection.mutable.Map[String, List[String]] = collection.mutable.Map[String, List[String]]()
	private var viewer:Viewer = _
	var graphPanel:ViewPanel = _
	var graph:MultiGraph = _
	private var fromViewer:ViewerPipe= _

	// Per gestire il loop eventi dal grafo
	var loop:Boolean = true

	// raw sarà la mappa degli archi del grafo: NODO -> LISTA DI NODI CONNESSI
	private def createGraph(raw:Map[String, List[String]], id:String = "dependencies") : Viewer = {
		// 1. Estraggo i nodi complessivi da rappresentare, guardando la distinct dall'unione delle liste e delle chiavi
		val nodi = raw.keySet ++ raw.values.flatten.toSet
		graph = new MultiGraph("embedded")

		// 2. Li aggiungo al grafo
		nodi.foreach(n => {
			val v = graph.addNode[MultiNode](n)
			v.setAttribute("ui.label", n)
			v.setAttribute("layout.weight", "4")
		})

		// 3. Scorro la mappa
		// per ciascuna coppia (NODO; LISTA), scorro la lista, ed aggiungo un nodo
		raw.foreach( (mapEntry) => mapEntry._2.foreach(dep => graph.addEdge[AbstractEdge](mapEntry._1 + dep, dep, mapEntry._1,  true)))

		// Visualizzazione
		graph.addAttribute("ui.stylesheet", "url(file:///" + CSS_PATH + ")")
		graph.addAttribute("ui.antialias")

		new Viewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD)

	}


	def updateGraph(graphMap:Map[String, List[String]], filterCombobox:Boolean = true): Unit = {
		if (graphPanel != null) {
			frame.remove(graphPanel)
			frame.remove(bottomLine)
		}
		viewer = createGraph(graphMap)
		graphPanel = viewer.addDefaultView(false)
		graphPanel.addMouseWheelListener(controller)

		frame.getContentPane.add(BorderLayout.CENTER, graphPanel)
		graphPanel.setLayout(new BorderLayout)

		if (graphMap.keySet.nonEmpty)
			graphPanel.add(BorderLayout.SOUTH, bottomLine)

		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
		viewer.enableAutoLayout()

		if (filterCombobox) {
			tf.removeAllItems()
			graphMap.keySet.toList.sorted.foreach(tf.addItem)
		}

		fromViewer = viewer.newViewerPipe
		fromViewer.addViewerListener(controller)
		fromViewer.addSink(graph)


		frame.setSize(1000, 800)
		SwingUtilities.updateComponentTreeUI(frame)

	}

	val frame = new JFrame("Lineage")

	System.getProperty("os.name") match {
		case "Mac OS X" => {
			// Reflection black magic to use a library that is only there in mac os without actually importing it, so that the same code compile on windows
			val application = Class.forName("com.apple.eawt.Application").getMethod("getApplication").invoke(null)
			application.getClass.getDeclaredMethod("setDockIconImage", classOf[Image]).invoke(application, new ImageIcon(LOGO_PATH).getImage)
			System.setProperty("apple.laf.useScreenMenuBar", "true")
			System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Lineage")
		}
		case _ =>frame.setIconImage(new ImageIcon(LOGO_PATH).getImage)
	}


	val menuBar: JMenuBar = new JMenuBar
	val fileMenu: JMenu = new JMenu("File")
	val menuImport: JMenu = new JMenu("Import script")
	menuImport.setToolTipText("Import a custom SQL script")

	val dialectsMenu: List[JMenuItem] = dialects.keys.map(dialect => new JMenuItem(dialect)).toList.sortBy(m => m.getText)
	dialectsMenu.foreach(d => {
		d.addActionListener((e:ActionEvent) => Controller.importDialect(dialects(d.getText)))
		menuImport.add(d)
	})

	//menuImport.addActionListener(controller)

	fileMenu.add(menuImport)

	val clearMenu: JMenuItem = new JMenuItem("Clear view")
	clearMenu.addActionListener(controller)

	fileMenu.add(clearMenu)

	menuBar.add(fileMenu)
	frame.setJMenuBar(menuBar)


	//Creazione Barra in basso
	val bottomLine: JPanel = new JPanel // the panel is not visible in output
	val searchLabel: JLabel = new JLabel("Search for table")
	val tf: JComboBox[String] = new JComboBox[String](graphMap.keys.toArray)
	tf.setEditable(true)
	val search: JButton = new JButton("Search")
	val reset: JButton = new JButton("Reset")
	//search.setIcon(new ImageIcon(search_icon))
	search.addActionListener(controller)
	reset.addActionListener(controller)

	val eastPanel = new JPanel

	val detailLabel = new JLabel("Table details")
	val boldFont = detailLabel.getFont().deriveFont(Font.BOLD, 14f)

	val selectedList = new JList[String](Array[String]())

	eastPanel.add(BorderLayout.NORTH, detailLabel)
	eastPanel.add(BorderLayout.CENTER, selectedList)
	eastPanel.setVisible(false)

	frame.getContentPane.add(BorderLayout.EAST, eastPanel)

	bottomLine.add(searchLabel)
	bottomLine.add(tf)
	bottomLine.add(search)
	bottomLine.add(reset)

	this.updateGraph(graphMap.toMap)

	frame.setVisible(true)


	while (loop) {
		if (fromViewer != null) fromViewer.pump()
	}


}