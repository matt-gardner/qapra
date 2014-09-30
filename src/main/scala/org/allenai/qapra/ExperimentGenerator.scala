package org.allenai.qapra

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable

// The point of this code is to produce a set of experiment files for use with PRA.  We're not
// going to run PRA here, just set up stuff so that other code can actually run it.
// There are two main tasks done here:
// 1 - setup the training/testing splits, the KB files, and other necessaries for running PRA
// 2 - simplify the SVO data (i.e., find the head of multiword NPs, lemmatize them)
object ExperimentGenerator {
  val user_home = System.getProperty("user.home")

  //--------
  // Inputs:
  //--------

  // This is a hand-written text file containing questions and tuples that should be able to answer
  // them.
  val question_triple_file = s"${user_home}/ai2/misc/question_tuples.txt"
  val simplified_question_triple_file = s"${user_home}/ai2/misc/simplified_question_triples.txt"

  // These triple files are the main input to the algorithm, the triples we'll use to try to answer
  // the questions.  We have two files for each set of triples - one is the original triple file,
  // one is a version that's passed through our SvoSimplifier.
  val triple_files_to_simplify = List(
    (s"${user_home}/ai2/misc/relation_sets/lower_case_extractions_as_triples.tsv",
     s"${user_home}/ai2/misc/relation_sets/lower_case_extractions_as_triples_simplified.tsv"),
    (s"${user_home}/ai2/misc/relation_sets/additional_svo.tsv",
     s"${user_home}/ai2/misc/relation_sets/additional_svo_simplified.tsv")
  )

  val training_relation_sets = List(
    s"${user_home}/ai2/misc/relation_sets/lower_case_extractions_as_triples_simplified.tsv",
    s"${user_home}/ai2/misc/relation_sets/additional_svo_simplified.tsv",
    s"${user_home}/ai2/misc/relation_sets/dart_triples.tsv"
  )

  val MAX_TRAINING_EXAMPLES = 3500

  //-------------------------
  // A few generated outputs:
  //-------------------------

  val pra_base = s"${user_home}/ai2/pra"
  val split_dir = s"${pra_base}/splits/ny_regents_dev/"
  val kb_dir = s"${pra_base}/kb_files/open_kb/"
  val relation_output_base = s"${user_home}/ai2/misc/relation_sets"
  val all_question_tuples = s"${relation_output_base}/all_question_tuples.tsv"
  val training_file_template = (split_dir: String, relation: String) => s"${split_dir}${relation}/training.tsv"

  // A parameter or two.
  val simplifyQuestionTriples = true

  // Some variables to control the flow of the code without commenting things out.  This is mostly
  // to avoid re-running some things that take a while if they're already done.
  val recreateSplitAndKbFiles = false
  val resimplifySvoData = false
  val recreateTrainingData = false

  def main(args: Array[String]) {
    // First go through the question tuples and decide which relations we need to learn models for.
    var relations: Iterable[String] = null
    if (recreateSplitAndKbFiles || recreateTrainingData) {
      println("Recreating split and KB files")
      relations = createSplitAndKbFiles(question_triple_file, all_question_tuples, split_dir, kb_dir)
    }
    println("Done creating split and KB files")

    if (resimplifySvoData) {
      println("Resimplifying SVO data")
      triple_files_to_simplify.map(SvoSimplifier.simplifySvoData)
    }
    println("Done simplifying SVO data")

    // Then we go through our triple files and find training data.
    if (recreateTrainingData) {
      println("Recreating training data")
      val triples = loadTrainingTriples(training_relation_sets)
      for (relation <- relations) {
        var relation_triples = triples(relation)
        println(s"Found ${relation_triples.size} training triples for relation $relation")
        if (relation_triples.length > MAX_TRAINING_EXAMPLES) {
          println(s"Trimming the training triples down to $MAX_TRAINING_EXAMPLES")
          relation_triples = relation_triples.slice(0, MAX_TRAINING_EXAMPLES)
        }
        val training_file = training_file_template(split_dir, relation)
        FileHelper.writeTriplesToRelationFile(relation_triples, training_file)
      }
    }
    println("Done creating training data")
  }

  def createSplitAndKbFiles(
      tuple_file: String,
      output_tuple_file: String,
      split_dir: String,
      kb_dir: String) = {
    println("Reading relation file")
    val questions = QuestionHelper.readQuestions(tuple_file)
    var all_triples = questions.flatMap(_.choices.flatMap(_.triples))
    if (simplifyQuestionTriples) {
      all_triples = all_triples.par.map(SvoSimplifier.simplifyTriple(_)).toList
    }
    val relation_triples = all_triples.map(triple => (triple._1._2, triple)).groupBy(_._1).mapValues(_.map(_._2))

    println("Outputting split files")
    new File(split_dir).mkdirs()
    val relations_file = new PrintWriter(split_dir + "relations_to_run.tsv")
    val all_triples_output = new PrintWriter(output_tuple_file)
    for ((relation, triples) <- relation_triples) {
      relations_file.println(relation)
      new File(split_dir + relation + "/").mkdirs()
      val output = new PrintWriter(split_dir + relation + "/testing.tsv")
      for (triple <- triples) {
        val arg1 = triple._1._1
        val arg2 = triple._1._3
        val correct = triple._2
        if (arg1 == null || arg2 == null) {
          println(s"Tuple relation was not processed correctly: $triple")
          println(s"arg1: $arg1, arg2: $arg2")
          System.exit(-1)
        }
        output.write(arg1 + "\t" + arg2 + "\t")
        if (correct) {
          output.write("1")
        } else {
          output.write("-1")
        }
        output.write("\n")
        all_triples_output.println(arg1 + "\t" + relation + "\t" + arg2 + "\t1")
      }
      output.close()
    }
    relations_file.close()
    all_triples_output.close()

    // Now we create the "KB files" for the experiment - this is mostly relation metadata that
    // would come from a KB, like relation inverses, ranges, and cluster ids.  We don't have any of
    // that, but my KbPraDriver doesn't handle some of these files being empty just yet.  So until
    // I fix that, we create an empty file or two here.
    println("Creating KB files")

    // We need an inverses file, but there aren't any known inverses, so we just create an empty
    // one.
    new File(kb_dir).mkdirs()
    new PrintWriter(kb_dir + "inverses.tsv").close()

    relation_triples.keys
  }

  def loadTrainingTriples(np_triple_files: Seq[String]) = {
    val triples = new mutable.HashMap[String, Array[RelationTriple]].withDefaultValue(new Array(0))
    for (triple_file <- np_triple_files) {
      FileHelper.addTriplesFromFile(triple_file, triples)
    }
    triples
  }

}
