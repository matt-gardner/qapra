# QAPRA

### Using PRA for multiple choice question answering

This started as a project at a short internship at the Allen Institute for Artificial Intelligence.
The basic idea is to take a multiple choice question, convert each choice into a sentence (or set
of sentences), then rank the sentences using a [PRA model](https://github.com/matt-gardner/pra).
The PRA model works by first breaking down the sentence into a set of SVO triples, then learning
models to score each SVO triple in each sentence, and finally combining the triple scores to give a
score for the sentence.

Using a very small development set of 24 questions that I judged to be "tupleable", the end result
was that the best model I learned got 17 of the 24 questions correct - not bad, I think, for a
proof-of-concept.

### Usage

The question triples are stored in the resources directory, and the `QuestionHelper` object in the
code is there to make it easy to interact with the questions (reading the triples from the
questions, scoring the answers to each question given PRA results, etc.).  `ExperimentGenerator`
will produce a lot of the input to PRA, which you can then run with PRA's `ExperimentRunner`,
though this code does not automatically generate graph and experiment spec files (see the PRA
documentation for that means).  The code is also missing the SVO triples that I used as input -
they are large enough that I didn't want to include them in this repository.  Ask me for them if
for some reason you would like them.  Also missing is the code I used to simplify SVO triples;
it's really simple (just find the lemmatized head of the NP, and lemmatize the verb phrase), but
it used AI2-internal parsing code, so I left it out of this repository.  I need to re-write that
using the Stanford parser.
