# QAPRA

### Using PRA for multiple choice question answering

This started as a project at a short internship at the Allen Institute for Artificial Intelligence.
The basic idea is to take a multiple choice question, convert each choice into a sentence (or set
of sentences), then rank the sentences using a PRA model.  The PRA model works by first breaking
down the sentence into a set of SVO triples, then learning models to score each SVO triple in each
sentence, and finally combining the triple scores to give a score for the sentence.

Using a very small development set of 24 questions that I judged to be "tupleable", the end result
was that the best model I learned got 17 of the 24 questions correct - not bad, I think, for a
proof-of-concept.

### Usage

Note that building this currently requires cloning my PRA repository (github.com/matt-gardner/pra)
and publishing it locally with `sbt "+ publish-local"`.  I need to get the graphchi code and the
PRA code in some central repository...
