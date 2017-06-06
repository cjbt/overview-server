package com.overviewdocs.query

import org.specs2.mutable.Specification

class QueryParserSpec extends Specification {
  sequential

  def repr(field: Field): String = field match {
    case Field.All => ""
    case Field.Title => "title:"
    case Field.Text => "text:"
  }

  def repr(node: Query): String = node match {
    case AllQuery => "ALL"
    case AndQuery(node1, node2) => s"AND(${repr(node1)},${repr(node2)})"
    case OrQuery(node1, node2) => s"OR(${repr(node1)},${repr(node2)})"
    case NotQuery(node) => s"NOT(${repr(node)})"
    case PhraseQuery(field, phrase) => s"${repr(field)}[$phrase]"
    case PrefixQuery(field, phrase) => s"${repr(field)}PREF([$phrase])"
    case FuzzyTermQuery(field, term, fuzziness) => s"${repr(field)}FUZZ([$term],${fuzziness.fold("AUTO")(_.toString)})"
    case ProximityQuery(field, phrase, slop) => s"${repr(field)}PROX([$phrase],${slop.toString})"
  }

  def parse(input: String): Either[SyntaxError,Query] = QueryParser.parse(input)

  def testGood(input: String, expected: String, description: String) = description in {
    parse(input).right.map(repr) must beEqualTo(Right(expected))
  }

  testGood("foo", "[foo]", "parse a term")
  testGood("foo bar", "[foo bar]", "parse space-separated terms as a phrase")
  testGood("foo AND bar", "AND([foo],[bar])", "parse AND as a boolean")
  testGood("foo OR bar", "OR([foo],[bar])", "parse OR as a boolean")
  testGood("NOT foo", "NOT([foo])", "parse NOT as a boolean")
  testGood("ANDroid ORxata NOThosaurus", "[ANDroid ORxata NOThosaurus]", "parse terms that start with operators")
  testGood("foo AND NOT bar", "AND([foo],NOT([bar]))", "give NOT precedence over AND (right-hand side)")
  testGood("NOT foo AND bar", "AND(NOT([foo]),[bar])", "give NOT precedence over AND (left-hand side)")
  testGood("'foo bar'", "[foo bar]", "parse single quotes")
  testGood("\"foo bar\"", "[foo bar]", "parse double quotes")
  testGood("“foo bar”", "[foo bar]", "parse smart quotes")
  testGood("'foo \"bar'", "[foo \"bar]", "allow double quote within single quotes")
  testGood("\"foo 'bar\"", "[foo 'bar]", "allow single quote within double quotes")
  testGood("“foo 'bar \"baz”", "[foo 'bar \"baz]", "parse single and double quotes within smart quotes")
  testGood("'foo \\'bar'", "[foo 'bar]", "allow escaping single quote with backslash")
  testGood("\"foo \\\"bar\"", "[foo \"bar]", "allow escaping double quote with backslash")
  testGood("'foo \\\\bar'", "[foo \\bar]", "allow escaping backslash")
  testGood("foo AND bar OR baz", "OR(AND([foo],[bar]),[baz])", "be left-associative (AND then OR)")
  testGood("foo OR bar AND baz", "AND(OR([foo],[bar]),[baz])", "be left-associative (OR then AND)")
  testGood("foo AND (bar OR baz)", "AND([foo],OR([bar],[baz]))", "group with parentheses")
  testGood("foo AND NOT (bar OR baz)", "AND([foo],NOT(OR([bar],[baz])))", "group with NOT and parentheses")
  testGood("(foo and bar) and not baz", "AND(AND([foo],[bar]),NOT([baz]))", "allow lowercase operators")
  testGood("('and' AND 'or') AND 'not'", "AND(AND([and],[or]),[not])", "allow quoting operators")
  testGood("foo~", "FUZZ([foo],AUTO)", "handle fuzziness")
  testGood("foo~2", "FUZZ([foo],2)", "handle fuzziness with integer")
  testGood("'foo bar'~3", "PROX([foo bar],3)", "handle proximity on quoted strings")
  testGood("NOT foo~2", "NOT(FUZZ([foo],2))", "give ~ (fuzzy) higher precedence than NOT")
  testGood("NOT 'foo bar'~2", "NOT(PROX([foo bar],2))", "give ~ (proximity) higher precedence than NOT")
  testGood("title:foo bar", "title:[foo bar]", "specify the title field")
  testGood("text:foo bar", "text:[foo bar]", "specify the text field")
  testGood("NOT title:foo bar AND bar", "AND(NOT(title:[foo bar]),[bar])", "give field higher precedence than NOT")
  testGood("title:foo~", "title:FUZZ([foo],AUTO)", "allow field on fuzzy query")
  testGood("title:foo bar~2", "title:PROX([foo bar],2)", "allow field on proximity query")
  testGood("foo bar*", "PREF([foo bar])", "allow prefix query")
  testGood("*", "[*]", "not allow zero-character prefix")
  testGood("f*", "[f*]", "now allow one-character prefix")
  testGood("fo*", "PREF([fo])", "allow two-character prefix")
  testGood("title:/path/subpath/*", "title:PREF([/path/subpath/])", "allow field+prefix query")
  testGood("foo* bar", "[foo* bar]", "ignore prefix operator in the middle of a phrase")
  testGood("NOT*", "PREF([NOT])", "parse NOT* as a phrase")
  testGood("NOT fo*", "NOT(PREF([fo]))", "parse NOT x* as one would expect")
}
