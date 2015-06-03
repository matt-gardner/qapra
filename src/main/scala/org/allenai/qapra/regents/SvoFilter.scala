package org.allenai.qapra.regents

import java.io.PrintWriter

import scala.collection.mutable
import scalax.io.Resource

object SvoFilter {
  val user_home = System.getProperty("user.home")

  val question_tuple_file = s"${user_home}/ai2/misc/relation_sets/all_question_tuples.tsv"
  val svo_file = s"${user_home}/data/svo_triples/hazy_thresholded_5/svo.tsv"
  val filtered_svo_file = s"${user_home}/ai2/misc/relation_sets/additional_svo.tsv"
  val ai2_svo_file = s"${user_home}/ai2/misc/relation_sets/extractions_as_triples.tsv"
  val outfile = s"${user_home}/ai2/misc/relation_sets/additional_svo.tsv"
  val compares_outfile = s"${user_home}/ai2/misc/relation_sets/compares.tsv"

  def main(args: Array[String]) {
    findRelationTriples("compares", Seq(filtered_svo_file, ai2_svo_file), compares_outfile)
  }

  def findRelationTriples(relation: String, svo_files: Seq[String], outfile: String) {
    val output = new PrintWriter(outfile)
    var lines_seen = 0
    for (svo_file <- svo_files;
         line <- Resource.fromFile(svo_file).lines()) {
      if (line.split("\t")(1) == relation) output.println(line)
      lines_seen += 1
    }
    println(s"Lines seen: $lines_seen")
  }

  def filterSvo(question_tuple_file: String, svo_file: String, outfile: String) {
    println("Reading question tuples")
    val words = new mutable.HashSet[String]
    val relations = new mutable.HashSet[String]
    for (line <- Resource.fromFile(question_tuple_file).lines()) {
      val pieces = line.split("\t")
      val arg1 = pieces(0)
      val rel = pieces(1)
      val arg2 = pieces(2)
      words ++= arg1.split(" ")
      words ++= arg2.split(" ")
      relations += rel
    }
    val final_words = words.toSet -- Seq("two", "a", "an", "the", "of", "its", "on", "only", "own",
      "other", "their")
    println(final_words.toList.sorted)
    val final_relations = relations.toSet

    println("Finding related tuples in SVO data")
    val output = new PrintWriter(outfile)
    var i = 0
    for (line <- Resource.fromFile(svo_file).lines()) {
      i += 1
      if (i % 1000000 == 0) println(i)
      val pieces = line.split("\t")
      val arg1 = pieces(0)
      val rel = pieces(1)
      val arg2 = pieces(2)
      val svo_words = (arg1.split("\t") ++ arg2.split("\t")).toSet
      if (svo_words.intersect(final_words).nonEmpty) {
        output.println(line)
      } else if (final_relations.contains(rel)) {
        output.println(line)
      }
    }
    output.close()
  }
}
