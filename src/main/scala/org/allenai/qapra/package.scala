package org.allenai

package object qapra {
  // A triple is (arg1, relation, arg2), or (subject, verb, object).
  type Triple = (String, String, String)
  // The assumption here is that the relation is known in a RelationTriple, so you only need to
  // specify the arguments.
  type RelationTriple = (String, String)
  type TripleWithCount = (Triple, Int)
  type TripleWithLabel = (Triple, Boolean)
  type TripleWithScore = (Triple, Double)
}
