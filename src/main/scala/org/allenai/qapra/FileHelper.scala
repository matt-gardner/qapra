package org.allenai.qapra

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable
import scalax.io.Resource
import scala.util.matching.Regex

object FileHelper {
  def addTriplesFromFile(triple_file: String, triples: mutable.Map[String, Array[RelationTriple]]) {
    println("Reading triples from " + triple_file)
    for (line <- Resource.fromFile(triple_file).lines()) {
      val pieces = line.split("\t")
      val arg1 = pieces(0).toLowerCase
      val rel = pieces(1).toLowerCase
      val arg2 = pieces(2).toLowerCase
      if (arg1.nonEmpty && arg2.nonEmpty) {
        triples(rel) :+= (arg1, arg2)
      }
    }
  }

  def insertRelationIntoTriple(relation: String, triple: RelationTriple) = (triple._1, relation, triple._2)

  val tripleLabels = Map(true -> "1", false -> "-1")
  def relationTripleWithLabelToString(t: TripleWithLabel): String = s"${t._1._1}\t${t._1._2}\t${t._1._3}\t${tripleLabels(t._2)}"
  def relationTripleWithCountToString(t: TripleWithCount): String = s"${t._1._1}\t${t._1._2}\t${t._1._3}\t${t._2}"
  def relationTripleToString(t: (String, String, String)): String = relationTripleWithCountToString((t, 1))
  def relationSOToString(t: (String, String)) = s"${t._1}\t${t._2}\t1"

  def writeTriplesToFile(relation: String, triples: Seq[RelationTriple], outfile: String) {
    writeTriplesToFile(triples.map(insertRelationIntoTriple(relation, _)), outfile)
  }

  def writeTriplesToRelationFile(triples: Seq[RelationTriple], outfile: String) {
    writeLinesToFile(triples.map(relationSOToString), outfile)
  }

  def writeTriplesToFile(triples: Seq[Triple], outfile: String) {
    writeLinesToFile(triples.map(relationTripleToString), outfile)
  }

  def writeTriplesWithCountToFile(triples: Seq[TripleWithCount], outfile: String) {
    writeLinesToFile(triples.map(relationTripleWithCountToString), outfile)
  }

  def writeLinesToFile(lines: Seq[String], outfile: String) {
    val output = new PrintWriter(outfile)
    lines.foreach(output.println(_))
    output.close()
  }
}
