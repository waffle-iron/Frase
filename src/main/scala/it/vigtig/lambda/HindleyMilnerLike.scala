package it.vigtig.lambda

/**
 * @author Hargreaves
 */
trait HindleyMilnerLike extends ASTLike {

  val FUNC: String = "Func"

  abstract trait Type

  abstract trait TMono extends Type

  abstract trait TPoly extends Type

  case class TFail(str: String) extends Type

  case class TInst(name: String) extends TMono

  case class TVar(name: String) extends TMono

  case class TPolyInst(name: String, args: Type*) extends TPoly

  case class TFunc(in: Type, out: Type) extends TPoly

  type Context = Map[Term, Type]

  def prettyType(t: Type): String = t match {
    case TPolyInst(name, args@_*) => s"""$name ${args.map(prettyType).mkString(" ")}"""
    case TFunc(in, out)           => s"${prettyType(in)} -> ${prettyType(out)}"
    case TVar(x)                  => x
    case TInst(x)                 => x
    case _                        => t.toString
  }

  /*
  https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Free_type_variables
  */
  def freeVars(t: Type): Set[Type] = t match {
    case TVar(_)                => Set(t)
    case TInst(_)               => Set()
    case TPolyInst(_, args@_ *) => args map freeVars reduce (_ ++ _)
  }

  /*
  https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Context_and_typing
  */
  def freeVars(ctx: Context): Set[Type] =
    ctx map {
      case (variable, tpe) => freeVars(tpe)
    } reduce (_ ++ _)

  /*
  https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Context_and_typing
  */
  def freeInContext(alpha: Type, ctx: Context) =
    ctx exists {
      case (variable, tpe) => alpha == tpe
    }

  /*
  https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Polymorphic_type_order
  */
  def moreGeneralOrEqual(a: Type, b: Type, ctx: Context): Boolean = (a, b) match {
    case (TVar(a), _)                 => true
    case (TInst(x), TInst(y))         => x == y
    case (a: TPolyInst, b: TPolyInst) =>

      a.name == b.name &&
        (a.args, b.args)
          .zipped
          .map(moreGeneralOrEqual(_, _, ctx))
          .foldLeft(true)(_ && _)

  }

  /*
  https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Degrees_of_freedom_instantiating_the_rules
   */
  def unify(a: Type, b: Type): Type = (a, b) match {
    //Both variables
    case (TVar(_), TVar(_)) => a

    //Assign inst to var
    case (TVar(_), _) => b
    case (_, TVar(_)) => a

    //Instances unify syntactically
    case (TInst(x), TInst(y)) => if (x != y) TFail(a + " != " + b) else a

    //Polytypes unify if type arguments have same length and unify pairwise
    case (a: TPolyInst, b: TPolyInst) if a.name != b.name               => TFail(s"name mismatch: $a.name != $b.name")
    case (a: TPolyInst, b: TPolyInst) if a.args.length != b.args.length => TFail(s"arg length mismatch: $a.args.length != $b.args.length")
    case (a: TPolyInst, b: TPolyInst)                                   =>
      val substitutions = (a.args, b.args)
        .zipped
        .map(unifySub)
        .toMap

      val newArgs = (a.args, b.args)
        .zipped
        .map(unify)
        .map(x => substitutions.getOrElse(x, x))
      TPolyInst(a.name, newArgs: _*)
  }

  def unifySub(a: Type, b: Type): (Type, Type) = (a, b) match {
    case (TVar(_), TInst(_)) => a -> b
    case (TInst(_), TVar(_)) => b -> a
    case _                   => (TFail(""), TFail(""))
  }


  def incrementVar(s: String): String = "" + (s.charAt(0) + 1).toChar

  val knownTypes: PartialFunction[Term, Type] = {
    case Integer(_)  => TInst("Int")
    case Bit(_)      => TInst("Bool")
    case Floating(_) => TInst("Float")
  }

  /*
  https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Algorithm_W
   */
  def w2(e: Term, ctx: Context, nextVar: String): (Type, String) = e match {
    case _ if knownTypes.isDefinedAt(e) => (knownTypes(e), nextVar)
    case Named(_, body)                 => w2(body, ctx, nextVar)
    case Id(_) if (ctx.contains(e))     => (ctx(e), nextVar)
    case Id(_)                          => (TVar(nextVar), incrementVar(nextVar))
    case Applic(e0, e1)                 =>
      val tauPrime = TVar(nextVar)
      val (tau0, next) = w2(e0, ctx, incrementVar(nextVar))
      val (tau1, next2) = w2(e1, ctx, next)
      //Does tau0 unify with tau1 -> tauPrime?
      val TPolyInst(_, _, out) = unify(tau0, TPolyInst(FUNC, tau1, tauPrime))
      (out, next2)
    case Abstr(id, e)                   =>
      val (tau,next) = w2(id,ctx,nextVar)
      val (tauPrime, next2) = w2(e, ctx + (id -> tau), next)
      (TPolyInst(FUNC, tau, tauPrime), next2)
    case _                              => (TFail(""), nextVar)
  }

  def inst(sigma: Type, newVar: () => String): Type =
    sigma match {
      case TInst(_)                 => sigma
      case TVar(_)                  => TVar(newVar())
      case TPolyInst(name, args@_*) =>
        TPolyInst(name, args map (t => inst(t, newVar)): _*)
    }

  def newTyper(next: String = "a") = {
    (e: Term) => {
      w2(e, Map(), next)
    }
  }


}




