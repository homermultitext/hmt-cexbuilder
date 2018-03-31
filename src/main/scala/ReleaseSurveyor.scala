package org.homermultitext.hmtcexbuilder
import edu.holycross.shot.scm._
import edu.holycross.shot.cite._
import java.io.File
import java.io.PrintWriter

/** Class for creating overviews of a published release of HMT
* instantiated as a CiteLibrary.
*
* @param lib The published release to survey.
* @param baseDir Directory where subdirectory for reports will be created.
* @param releaseId Identifier for this release, used to create name
* of subdirectory where reports are written.
*/
case class ReleaseSurveyor(lib: CiteLibrary, baseDir: String, releaseId: String) {

  /** Base URL for references to HMT Image Citation Tool.*/
  val imtIctBase = "http://www.homermultitext.org/ict2/"

  /** Base URL to an installation of IIPSrv with HMT data. */
  val hmtIipSrvBase = "http://www.homermultitext.org/iipsrv?OBJ=IIP,1.0&FIF=/project/homer/pyramidal/deepzoom/"

  /** Compose IIPSrv URL for an image. */
  def iipSrvUrl(img: Cite2Urn,  width: Int = 1000, baseUrl: String = hmtIipSrvBase): String = {
    val trail = s"&WID=${width}&CVT=JPEG"
    val imageOnly = List(baseUrl, img.namespace, img.collection, img.version, img.dropExtensions.objectOption.get).mkString("/") + s".tif"

    img.objectExtensionOption match {
      case None => imageOnly +  trail
      case roi:  Some[String] =>imageOnly + "&RGN=" + roi.get + trail
    }
  }

  /** Map of topic label to subdirectory name. */
  val subdirForTopic = Map (
    "images" -> "images",
    "tbs" -> "codices-papyri",
    "texts" -> "texts",
    "dse" -> "dse"
  )


  // assemble complete suite of reports
  def overview = {
    val indexText = homePage
    val indexFile = new File(releaseDir, "index.md")
    new PrintWriter(indexFile) {write(indexText); close; }
    imageOverview(dirMap("images"))
    tbsOverview(dirMap("tbs"))
  }


  /** Compose a home page, in markdown format, for this report.
  */
  def homePage: String = {
    val hdr = "# Overview of HMT project release **" + releaseId +"**\n\n" +
    fileLayoutBoilerPlate  +
    "## Collection data models\n\n"

    val dm = for (dm <- lib.dataModels.get)  yield {
      "\n**" + dm.label + s"** (`${dm.model}`) applies to \n\n-   " + lib.collectionsForModel(dm.model).mkString("\n-   ")
    }

    val txtsHdr = "\n\n## Texts\n\nThe OHOC2 model of citable texts applies to \n\n"
    val textCatalog = lib.textRepository.get.catalog
    val exemplarList = for (txt <- textCatalog.labelledExemplars) yield {
      s"-   ${textCatalog.groupName(txt.urn)}, *${textCatalog.workTitle(txt.urn)}* (${txt.label}: `${txt.urn}`)"
    }
    val versionList =  for (txt <- textCatalog.labelledVersions) yield {
        s"-   ${textCatalog.groupName(txt.urn)}, *${textCatalog.workTitle(txt.urn)}* (${txt.label}: `${txt.urn})`"
      //s"-   ${txt.label} (${txt.urn})"
    }
    hdr + dm.mkString("\n") + txtsHdr+ exemplarList.mkString("\n") + versionList.mkString("\n")
  }

  /** Find root directory as a File object,
  * ensuring that it has been created.
  */
  def rootDir : File = {
    val root = new File(baseDir)
    if (!root.exists){
      root.mkdir
    }
    root
  }

  /** Find directory for reports on this release, as a File object,
  * ensuring that it has been created.
  */
  def releaseDir : File = {
    val reportDir = new File(rootDir, s"${releaseId}-summary")
    if (!reportDir.exists){
      reportDir.mkdir
    }
    reportDir
  }

  /** Construct map of required subdirectories.
  */
  def dirMap: Map[String, File] = {
    val subdirMap = for (topic <- subdirForTopic.keySet) yield {
        val subdir = new File(releaseDir, subdirForTopic(topic))
        subdir.mkdir
        require(subdir.exists, s"Did not create directory for topic ${topic}")
        (topic, subdir)
    }
    subdirMap.toMap
  }



  /** Compose report on collections of images modelled as imagemodel objects.
  *
  * @param imageDir Directory where image reports should be written.
  * @param columns Width of output table in columns.
  * @param thumbSize Widthof thumbnail images in pixels.
  */
  def imageOverview(imageDir: File, columns: Int = 5, thumbSize: Int = 400) = {
    val binaryImageModel = Cite2Urn("urn:cite2:cite:datamodels.v1:imagemodel")
    val citeCatalog = lib.collectionRepository.get.catalog

    for (urn <- lib.collectionsForModel(binaryImageModel)) {
      val objects = lib.collectionRepository.get.objectsForCollection(urn)

      val hdr = "# Summary for image collection\n\n" +
      s"**${citeCatalog.collection(urn).get.collectionLabel}** (`${urn}`):  total of ${objects.size} images.\n\n"

      // format a markdown string for each image
      val imgSet = for(obj <- objects) yield {
         s"![${obj.urn}](${iipSrvUrl(obj.urn, thumbSize)}) <br/>${obj.label}"
      }
      val imgRecords = imgSet.toSeq.toVector
      // place the images in a tablewith with specified width (in cells)
      val rows = for (i <- 0 until imgRecords.size) yield {
          val oneBasedIndex = i + 1
          if (oneBasedIndex % columns == 0){
            val sliver = imgRecords.slice( oneBasedIndex - columns, oneBasedIndex)
            "| " + sliver.mkString(" | ") + " |"
          } else ""
      }
      val sizedRows = rows.filter(_.nonEmpty)

      // catch any left over if rows/columns didn't work out evenly
      val remndr =  imgRecords.size % columns
      val trailer = if (remndr != 0)  {
        val sliver = imgRecords.slice(imgRecords.size - remndr, imgRecords.size)
        val pad = List.fill( columns - remndr - 1)( " | ").mkString
        "| " + sliver.mkString(" | ") + pad + " |\n"
      } else ""

      val tableLabels =  List.fill(columns)("| ").mkString + "|\n"
      val tableSeparator =  List.fill(columns)("|:-------------").mkString + "|\n"

      val reportFile = new File(imageDir, urn.collection + "-summary.md")
      new PrintWriter(reportFile){write(hdr + tableLabels +  tableSeparator + sizedRows.mkString("\n") + trailer  +  "\n\n") ; close;}
      
    }   // for each collection
  }

  /** Compose report on collections of text-bearing surfaces.
  *
  * @param tbsDir Directory where TBS reports should be written.
  */
  def tbsOverview(tbsDir: File, columns: Int = 5) = {
    def tbsModel = Cite2Urn("urn:cite2:cite:datamodels.v1:tbsmodel")
    val citeCatalog = lib.collectionRepository.get.catalog



    for (urn <- lib.collectionsForModel(tbsModel)) {
      val objects = lib.collectionRepository.get.objectsForCollection(urn)
      val hdr = "# Summary for artifact with texts\n\n" +
        s"**${citeCatalog.collection(urn).get.collectionLabel}** (`${urn}`):  total of ${objects.size} surfaces.\n\n"

      val urnSeq = objects.map(_.urn)

/*
      val surfaceSet = for (k <- objects.objectMap.keySet) yield {
         s"${k} -> ${objects.objectMap(k)}"
      }*/
      println(hdr + "\n" + urnSeq.mkString("\n"))
    }
  }

  // overview of DSE triangle
  def dseOverview(dseDir: File)= {
    val dseModel = Cite2Urn("urn:cite2:cite:datamodels.v1:dse")
  }


  // overview of ohco2 editions
  def textOverview(textDir: File) = {
/*
println("OHOC2 model applies to \n\t")
for (txt <- lib.textRepository.get.catalog.labelledExemplars) {
  println("\t"+ txt)
}
for (txt <- lib.textRepository.get.catalog.labelledVersions) {
  println(s"\t${txt.label} (${txt.urn})")
}
    */
  }

  /** Compose message about file layout. */
  def fileLayoutBoilerPlate: String =  {
    val folderList = for (topic <- subdirForTopic.keySet) yield {
      "-   `" + subdirForTopic(topic) + "`"
    }
    "Note: more details are provided for specific contents of this release in the associated folders:\n\n" + folderList.mkString("\n") + "\n\n"
  }

}
