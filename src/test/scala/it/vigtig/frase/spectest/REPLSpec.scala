package it.vigtig.frase.spectest

import java.io.ByteArrayInputStream

import it.vigtig.lambda.REPL
import it.vigtig.lambda.REPL.InvalidProgramException
import org.scalatest.{FlatSpec, Matchers}

class REPLSpec
  extends FlatSpec
    with
    Matchers {

  behavior of "REPL"

  it should "parse a simple program" in
  {
    val testProgram =
      "fib = n . (<= n 2) (1) ((+ (fib (- n 2)) (fib (- n 1))))" +
      "\nset Foo = Bar or Baz" +
      "\nfac = 0 . 1" +
      "\nfac = n . * n (fac (- n 1))" +
      "\n:exit\n"


    val in = new ByteArrayInputStream(testProgram.getBytes)
    Console.withIn(in)
    {
      try
      {
        REPL.main(Array.empty)
        1 shouldBe 1
      } catch
      {
        case InvalidProgramException(msg) =>
      }
    }


  }

}
