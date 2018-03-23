package org.homermultitext.hmtcexbuilder
import scala.xml._
import java.io.File
import scala.io.Source

object DataCollector {


  /** Recursively collect contents of all text-node
  * descendants of a given node.
  * @param n Node to collect from.
  * @param buff Buffer for collecting text contents.
  * @return A single String with all text from n.
  */
  def collectXmlText(n: xml.Node): String = {
    collectXmlText(n,"")
  }

  /** Recursively collect contents of all text-node
  * descendants of a given node.
  * @param n Node to collect from.
  * @param buff Buffer for collecting text contents.
  * @return A single String with all text from n.
  */
  def collectXmlText(n: xml.Node, s: String): String = {
    var buff = StringBuilder.newBuilder
    buff.append(s)
    n match {
      case t: xml.Text => {
        buff.append(t.text)
      }

      case e: xml.Elem => {
        for (ch <- e.child) {
          buff = new StringBuilder(collectXmlText(ch, buff.toString))
        }
      }
    }
    buff.toString
  }


  /** Recursively collect text contents from a string
  * of well-formed XML.
  *
  * @param s String of well-formed XML..
  */
  def collectXmlText(s: String): String = {
    val root = XML.loadString(s)
    collectXmlText(root, "")
  }


  /** Find set of files in a given directory with name
  * matching a given extension.
  *
  * @param dir Directory to look in.
  * @param extension File extension to match.
  */
  def filesInDir(dir: String, extension: String = "xml"): Set[File] = {
    val libraryDir = new File(dir)
    val fileVector = libraryDir.listFiles.filter(_.isFile).toVector
    val files = fileVector.filter(_.getName.endsWith(extension))
    files.toSet
  }


  /** Create a single String concatenating content
  * of all CEX files in a given directory.
  *
  * @param dir Directory to collect content from.
  */
  def compositeCex(dir: String): String = {
    def fileSet = filesInDir(dir, "cex")
    def txts = for (f <- fileSet) yield {
      val lines = scala.io.Source.fromFile(f).getLines.toVector
      lines.mkString("\n")
    }
    txts.toSeq.mkString("\n") + "\n"
  }

}
