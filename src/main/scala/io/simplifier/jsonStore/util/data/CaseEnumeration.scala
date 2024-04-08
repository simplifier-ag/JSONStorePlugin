package io.simplifier.jsonStore.util.data

import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
import org.json4s.{JValue, _}

import java.util.concurrent.atomic.AtomicReference
import scala.reflect.ClassTag
import scala.util.{Failure, Try}


/**
  * Enumeration implementation without the problems of scala.Enumeration.
  * See https://underscore.io/blog/posts/2014/09/03/enumerations.html
  * Implementation similar to https://gist.github.com/viktorklang/1057513
  */
// TODO Support for Case Objects wit explicit values list
abstract class CaseEnumeration(strict: Boolean = true) {

  /**
    * This is a type that needs to be found in the implementing class
    */
  type EnumVal <: Value

  /**
    * Stores our enum values
    */
  private val _values = new AtomicReference(Vector[EnumVal]())

  /**
    * Adds an EnumVal to our storage, uses CAS to make sure it's thread safe, returns the ordinal
    */
  @scala.annotation.tailrec
  private final def addEnumVal(newVal: EnumVal): Int = {
    val oldVec = _values.get
    val newVec = oldVec :+ newVal
    if (_values.compareAndSet(oldVec, newVec)) {
      newVec.indexWhere(_ eq newVal)
    } else {
      addEnumVal(newVal)
    }
  }

  /**
    * All values of the enumeration in order.
    */
  lazy val values: Vector[EnumVal] = _values.get


  /**
    * This is the trait that we need to extend our EnumVal type with, it does the book-keeping for us
    */
  protected trait Value {

    //Enforce that no one mixes in Value in a non-EnumVal type
    self: EnumVal =>

    implicit val enumFromStringUnmarshaller: FromStringUnmarshaller[EnumVal] = Unmarshaller.strict[String, EnumVal] {
      name => getByName(name).getOrElse(throw new IllegalArgumentException(s"No enum value for: [${mappingError(name)}]."))
    }

    /**
      * Ordinal number of the enum value
      */
    final val ordinal = addEnumVal(this) //Adds the EnumVal and returns the ordinal

    /**
      * Name of the enum value
      */
    def name: String

    /**
      * Checks whether this enumeration value is part of a provided sequence of enumeration values.
      *
      * @param enumSeq  the sequence to check the begin-part-of relation.
      *
      * @return         <b>true</b> if this enumeration value is part of the provided sequence, <b>false</b> otherwise.
      */
    def isPartOf(enumSeq: Seq[EnumVal]): Boolean = enumSeq.contains(this)

    override def toString: String = name //And that name is used for the toString operation

    override def equals(other: Any): Boolean = this eq other.asInstanceOf[AnyRef]

    override def hashCode: Int = 31 * (this.getClass.## + name.## + ordinal)

  }

  /**
    * Return a `Value` from this `CaseEnumeration` whose name matches
    * the argument `s`.
    *
    * @param  s an `CaseEnumeration` name
    *
    * @return the `Value` of this `CaseEnumeration` if its name matches `s`
    *
    * @throws   NoSuchElementException if no `Value` with a matching
    *                                  name is in this `Enumeration`
    */
  def withName(s: String): EnumVal = (if (strict) values.find(_.name == s) else values.find(v => sanitizeName(v.name) == sanitizeName(s)))
    .getOrElse(throw new NoSuchElementException(s"No value found for: [$s]."))

  def withName(s: String, error: Throwable): EnumVal = Try(withName(s)).recoverWith { case _ => Failure(error) }.get

  def withNameOpt(s: String): Option[EnumVal] = if (strict) values.find(_.name == s) else values.find(v => sanitizeName(v.name) == sanitizeName(s))

  def withDefault(s: String, default: EnumVal): EnumVal = withNameOpt(s).getOrElse(default)

  def withDefault(s: Option[String], default: EnumVal): EnumVal = s.flatMap(withNameOpt).getOrElse(default)

  def isValid(name: String): Boolean = this.values.exists(_.name == name)

  def unapply(name: String): Option[EnumVal] = this.values.find(_.name == name)

  def getByName(name: String): Option[EnumVal] = unapply(name)

  def getValues: Seq[String] = values.map(_.name)

  def useFallback[T](loggingMessage: String, loggingAction: String => T, fallback: EnumVal): EnumVal = {
    loggingAction(loggingMessage)
    fallback
  }

  protected def sanitizeName(name: String): String = {
    name
      .replace("-", "_")
      .replace(" ", "_")
      .toUpperCase
  }

  /* Serializer/Deserializer */

  def jsonSerializer(implicit ct: ClassTag[EnumVal]): Serializer[EnumVal] = new EnumSerializer

  protected def mappingError(value: String): String = s"Can't convert [$value] to [$getClass]."

  protected def mappingErrorMissing: String = s"Missing value for $getClass"

  protected def NotDeserializableFromTypeInfo[T](ti: TypeInfo, value: T, renderer: T => String, error: Throwable): MappingException = {
    val className: String = Option(ti.clazz).flatMap(clazz => Option(clazz.getName)).getOrElse("-- No Classname Provided --")
    val classString: String = getClassName(this, "This'")
    val errorTypeString: String = getClassName(error, "Exception Type's")
    val valueString: String = Try(renderer(value)).getOrElse("-- Provided Value Not Renderable --")
    new MappingException(s"The deserialization into a [$classString] for the provided class: [$className] of the TypeInfo from the provided value: [$valueString] was not possible " +
      s"due to an error of the type: [$errorTypeString].", new Exception(error))
  }

  protected def jsonDeserialize(implicit formats: Formats, ct: ClassTag[EnumVal]): PartialFunction[(TypeInfo, JValue), EnumVal] = {
    case (ti@TypeInfo(clazz, _), json) if ct.runtimeClass.isAssignableFrom(clazz) =>
      json match {
        case JString(value) if isValid(value) => Try(withName(value)).fold(e => throw NotDeserializableFromTypeInfo[String](ti, value, stringRenderer, e), value => value)
        case JString(other) => throw new MappingException(mappingError(other))
        case JNothing => throw new MappingException(mappingErrorMissing)
        case value => throw new MappingException(mappingError(value.toString))
      }
  }

  protected def jsonSerialize(implicit format: Formats, ct: ClassTag[EnumVal]): PartialFunction[Any, JValue] = {
    case ct(value) => JString(value.name)
  }

  private[this] class EnumSerializer(implicit ct: ClassTag[EnumVal]) extends Serializer[EnumVal] {
    def deserialize(implicit formats: Formats): PartialFunction[(TypeInfo, JValue), EnumVal] = jsonDeserialize(formats, ct)

    def serialize(implicit formats: Formats): PartialFunction[Any, JValue] = jsonSerialize(formats, ct)
  }

  private[this] def getClassName(clazz: Any, clazzType: String): String = {
    Try(clazz.getClass.getSimpleName).toOption
      .orElse(Try(clazz.getClass.getCanonicalName).toOption)
      .orElse(Try(clazz.getClass.getName).toOption)
      .fold(s"-- $clazzType Classname Not Retrievable --")(c => c.stripSuffix("$"))
  }

  private[this] def stringRenderer(string: String) = string

}
