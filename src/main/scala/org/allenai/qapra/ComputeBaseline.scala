package org.allenai.qapra

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable
import scalax.io.Resource

import edu.cmu.ml.rtw.users.matt.util.{FileHelper => PraFileHelper}

// This class implements a simple baseline - just do a lookup on each SVO triple to see if it's
// already in the set.  If it is, return the triple's count as our score, else return 0.
object ComputeBaseline {

  def scoreTriples(split_dir: String, results_dir: String, svo_files: Seq[String]) {
    val relation_files = PraFileHelper.recursiveListFiles(new File(split_dir), """.*testing.tsv""".r)
    val triples = readSvoFiles(svo_files)
    makeSettingsFile(results_dir, split_dir, svo_files)
    for (relation_file <- relation_files) {
      scoreRelationTriples(relation_file, results_dir, triples)
    }
  }

  def readSvoFiles(svo_files: Seq[String]): Map[Triple, Int] = {
    println("Reading SVO files")
    val triples = new mutable.HashMap[Triple, Int].withDefaultValue(0)
    for (svo_file <- svo_files;
         line <- Resource.fromFile(svo_file).lines()) {
      val fields = line.split("\t")
      val triple = (fields(0), fields(1), fields(2))
      val count = fields(3).toInt
      triples(triple) += count
    }
    triples.toMap.withDefaultValue(0)
  }

  def makeSettingsFile(results_dir: String, split_dir: String, svo_files: Seq[String]) {
    println("Making the settings file")
    new File(results_dir).mkdirs()
    val out = new PrintWriter(results_dir + "settings.txt")
    out.println("BASELINE - JUST LOOK UP TRIPLE IN SVO DATA")
    out.println(s"Splits used: $split_dir")
    for (svo_file <- svo_files) {
      out.println(s"SVO file: $svo_file")
    }
    out.close()
  }

  def readTestTriples(file: File, relation: String) = {
    println(s"Reading test triples for relation $relation")
    val triples = new mutable.HashMap[String, List[String]].withDefaultValue(Nil)
    for (line <- Resource.fromFile(file).lines()) {
      val fields = line.split("\t")
      triples.update(fields(0), fields(1) :: triples(fields(0)))
    }
    triples.toMap
  }

  def scoreRelationTriples(relation_file: File, results_dir: String, triples: Map[Triple, Int]) {
    println(s"Scoring triples in test split $relation_file")
    val relation = relation_file.getParentFile.getName
    val test_triples = readTestTriples(relation_file, relation)
    val relation_dir = s"$results_dir/$relation/"
    new File(relation_dir).mkdirs()
    val scores_file = s"$relation_dir/scores.tsv"
    val out = new PrintWriter(scores_file)
    for (source <- test_triples.keys) {
      for (target <- test_triples(source)) {
        val score = triples((source, relation, target))
        out.println(s"$source\t$target\t$score\t")
      }
      out.println()
    }
    out.close()
  }

  def main(args: Array[String]) {
    scoreTriples(
      "/home/mg1/ai2/pra/splits/ny_regents_dev/",
      "/home/mg1/ai2/pra/results/baseline_ai2_svo/",
      Seq(
        "/home/mg1/ai2/misc/relation_sets/ai2.tsv",
        "/home/mg1/ai2/misc/relation_sets/svo.tsv"
      )
    )
  }
}
