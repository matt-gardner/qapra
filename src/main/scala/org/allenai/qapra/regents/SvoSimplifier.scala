package org.allenai.qapra.regents

import scala.collection.mutable
import scala.collection.JavaConversions._
import scalax.io.Resource

import java.io.PrintWriter
import java.util.concurrent.ConcurrentLinkedQueue

object SvoSimplifier {
  def simplifySvoData(triple_and_simplified_files: (String, String)) {
    val np_triple_file = triple_and_simplified_files._1
    val simplified_output_file = triple_and_simplified_files._2
    val failure_file = np_triple_file + ".failed"
    val saved_simplification_file = np_triple_file + ".saved_simplifications"
    val to_simplify = new mutable.ListBuffer[TripleWithCount]
    println(s"Reading saved simplifications from $saved_simplification_file")
    val saved_simplifications = readSavedSimplifications(saved_simplification_file)
    println(s"Reading triple file $np_triple_file, looking for unsimplified SVO triples")
    for (line <- Resource.fromFile(np_triple_file).lines()) {
      val pieces = line.split("\t")
      val arg1 = pieces(0)
      val rel = pieces(1)
      val arg2 = pieces(2)
      val count = pieces(3).toInt
      val triple_with_count = ((arg1, rel, arg2), count)
      if (!saved_simplifications.contains(triple_with_count)) {
        to_simplify += triple_with_count
      }
    }
    println(s"Found ${to_simplify.length} triples to simplify")
    val results = new ConcurrentLinkedQueue[(TripleWithCount, TripleWithCount)]
    val failures = new ConcurrentLinkedQueue[Triple]
    def simplifier(svo: TripleWithCount) {
      try {
        val simplified = simplifyTriple(svo)
        if (simplified == null) {
          failures.add(svo._1)
        } else {
          results.add((svo, simplified))
        }
      } catch {
        case (e: Exception) => failures.add(svo._1)
      }
    }
    to_simplify.par.map(simplifier)
    println(s"Done simplifying, writing failures to $failure_file")
    FileHelper.writeTriplesToFile(failures.toSeq, failure_file)
    println(s"Writing simplifications to $saved_simplification_file")
    writeSavedSimplificationsToFile(saved_simplifications ++ results, saved_simplification_file)
    val relations = results.map(_._2) ++ saved_simplifications.map(_._2)
    val collapsed = relations.groupBy(_._1).mapValues(_.foldLeft(0)(_ + _._2))
    println(s"Writing simplified triples to $simplified_output_file")
    FileHelper.writeTriplesWithCountToFile(collapsed.toList, simplified_output_file)
  }

  def readSavedSimplifications(saved_simplification_file: String) = {
    val simplifications = new mutable.HashMap[TripleWithCount, TripleWithCount]
    for (line <- Resource.fromFile(saved_simplification_file).lines()) {
      val fields = line.split("\t")
      val arg1 = fields(0)
      val rel = fields(1)
      val arg2 = fields(2)
      val count = fields(3).toInt
      val original_triple = ((arg1, rel, arg2), count)
      val simplifiedArg1 = fields(4)
      val simplifiedRel = fields(5)
      val simplifiedArg2 = fields(6)
      val simplifiedCount = fields(7).toInt
      val simplified_triple = ((simplifiedArg1, simplifiedRel, simplifiedArg2), simplifiedCount)
      simplifications(original_triple) = simplified_triple
    }
    simplifications.toMap
  }

  def writeSavedSimplificationsToFile(
      simplifications: Map[TripleWithCount, TripleWithCount],
      saved_simplification_file: String) {
    val output = new PrintWriter(saved_simplification_file)
    simplifications.map(x => output.println(simplificationToString(x)))
    output.close()
  }

  def simplificationToString(simplification: (TripleWithCount, TripleWithCount)) = {
    val original = simplification._1
    val simplified = simplification._2
    val fields = new mutable.ListBuffer[String]
    fields += original._1._1
    fields += original._1._2
    fields += original._1._3
    fields += original._2.toString
    fields += simplified._1._1
    fields += simplified._1._2
    fields += simplified._1._3
    fields += simplified._2.toString
    fields.mkString("\t")
  }

  def simplifyTriple[A](triple: (Triple, A)): (Triple, A) = {
    // I used some AI2-internal tools to do the parsing that backed the SVO simplification.  I need
    // to reimplement this for the public version of this code, probably using the Stanford-backed
    // parser that I started in QuestionParser.
    println("NEED TO REIMPLEMENT THIS")
    triple
  }
}
