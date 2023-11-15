package io.simplifier.jsonStore.util.json

import org.json4s.JsonAST.{JField, JObject}
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{JArray, JNothing, JValue}

import java.nio.charset.StandardCharsets
import scala.util.Try




trait JSONCompatibility {
	/**
	 * wrapper for parse() function, emulating liftweb behavior: "" parses to JNothing
	 */
	@inline def parseJsonOrEmptyString(s: String): JValue = if (s.isEmpty) JNothing else parse(s)
	@inline def parseJsonOrEmptyString(b: Array[Byte]): JValue = parseJsonOrEmptyString(new String(b, StandardCharsets.UTF_8))



	case class LegacySearch(json:JValue) {

		import io.simplifier.pluginbase.util.json.NamedTupleAccess._

		def \\(key:String): JValue = {
			json \\ key match {
				case JNothing => JNothing

				// in case it is not found at all
				case JObject(List()) => JObject(List())

				// in case there are multiple matches
				case multiple:JObject if multiple.obj.size>1 && multiple.obj.head.name==key => multiple

				// in case there is a single match
				case item => JObject(JField(key,item))
			}
		}

		def \(key:String): JValue = {
			if (json.isInstanceOf[JArray])
				json \ key match {
					case a:JArray if a.arr.size == 1 => a.arr.head
					case other => other
				}
			else
				json \ key
		}

	}


	/**
	 * Fix JSON features that were parsed by liftweb, but won't be accepted by JSON4S:
	 * - strictly escape control chars inside string literals
	 * - add a missing comma between array elements and object fields
	 * - skip additional commas before/between/after array elements and object fields
	 *
	 * @param data         input string
	 * @param removeCommas if true, additional commas before/between/after array elements and object fields are deleted
	 *                     rather than throwing an error
	 * @return json string
	 */
	def fixInputString(data: String, removeCommas: Boolean = true): Try[String] = Try {
		val CHAR_QUOTE = '"'
		val CHAR_OBJECT_BEGIN = '{'
		val CHAR_OBJECT_END = '}'
		val CHAR_ARRAY_BEGIN = '['
		val CHAR_ARRAY_END = ']'

		val CHAR_COMMA = ','
		val CHAR_COLON = ':'
		val CHAR_BACKSLASH: Char = 0x005C
		val CHAR_TAB: Char = 0x0009
		val CHAR_LF: Char = 0x000A
		val CHAR_CR: Char = 0x000D
		val WHITESPACE_CHARS = Seq(' ', CHAR_TAB, CHAR_LF, CHAR_CR)

		val STRING_CHAR_REPLACEMENTS = Map(
			CHAR_TAB -> "\\t", // Tab
			CHAR_LF -> "\\n", // Line feed
			CHAR_CR -> "\\r", // Carriage return
			0x000C -> "\\f", // Form feed
			0x0008 -> "\\b" // Backspace
		)
		val NUMBER_CHARS = Seq('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '-')
		val NUMBER_CHARS_EXT = NUMBER_CHARS ++ Seq('.', 'e', 'E')

		var pos = 0
		var line = 1
		val out: StringBuilder = StringBuilder.newBuilder

		val it = data.toIterator
		var char: Option[Char] = None

		def next(): Unit = {
			char = if (it.hasNext) {
				val c = it.next()
				if (c == CHAR_LF) {
					pos = 1
					line += 1
				} else {
					pos += 1
				}
				Some(c)
			} else None
		}

		def readWhile(pred: Char => Boolean)(action: Char => Unit): Unit = {
			while (char.exists(pred)) {
				action(char.get)
				next()
			}
		}

		def writeChar(c: Char): Unit =
			out += c

		def whitespaces(): Unit =
			readWhile(WHITESPACE_CHARS.contains)(writeChar)

		def number(): Unit =
			readWhile(NUMBER_CHARS_EXT.contains)(writeChar)

		def symbol(): Unit =
			readWhile(_.isLetter)(writeChar)

		def string(): Unit = {
			assert(char.contains(CHAR_QUOTE), s"string begins with $CHAR_QUOTE")
			writeChar(CHAR_QUOTE)
			next()

			readWhile(_ != CHAR_QUOTE) {
				case c if STRING_CHAR_REPLACEMENTS.contains(c) =>
					// fix non escaped character
					STRING_CHAR_REPLACEMENTS(c) foreach writeChar
				case CHAR_BACKSLASH =>
					next()
					char foreach { c =>
						writeChar(CHAR_BACKSLASH)
						writeChar(c)
					}
				case c =>
					writeChar(c)
			}
			writeChar(CHAR_QUOTE)
			next()
		}

		def readAheadCommasAndWhitespaces(): String = {
			val spc = StringBuilder.newBuilder
			while (WHITESPACE_CHARS.exists(char.contains)) {
				spc + char.get
				next()
			}

			// fix: only take the 1st comma in a row
			var first = true
			while (char.contains(CHAR_COMMA)) {
				if (first) {
					spc + char.get
				} else if (!removeCommas) {
					throw MalformedJsonString(s"Expect value but found '$CHAR_COMMA'", line, pos, data)
				}
				next()
				while (WHITESPACE_CHARS.exists(char.contains)) {
					spc + char.get
					next()
				}
				first = false
			}
			spc.result()
		}

		def jsonArray(): Unit = {
			assert(char.contains(CHAR_ARRAY_BEGIN), s"array begins with $CHAR_ARRAY_BEGIN")
			writeChar(CHAR_ARRAY_BEGIN)
			next()

			val whitespaceBefore = readAheadCommasAndWhitespaces()
			if (whitespaceBefore.contains(CHAR_COMMA) && !removeCommas) {
				throw MalformedJsonString(s"Expect value but found '$CHAR_COMMA'", line, pos, data)
			}
			// fix: remove leading comma(s)
			whitespaceBefore filterNot (_ == CHAR_COMMA) foreach writeChar

			while (char.isDefined && !char.contains(CHAR_ARRAY_END)) {
				value()

				val separator = readAheadCommasAndWhitespaces()
				if (char.contains(CHAR_ARRAY_END)) {
					if (separator.contains(CHAR_COMMA) && !removeCommas) {
						throw MalformedJsonString(s"Expect array element but found '$CHAR_COMMA'", line, pos, data)
					}
					// fix: remove trailing comma(s)
					separator filterNot (_ == CHAR_COMMA) foreach writeChar
				} else {
					if (!separator.contains(CHAR_COMMA)) {
						// fix: add missing comma
						writeChar(CHAR_COMMA)
					}
					separator foreach writeChar
				}
			}

			if (!char.contains(CHAR_ARRAY_END))
				throw MalformedJsonString(s"Expect '$CHAR_ARRAY_END', '$CHAR_COMMA' or array element", line, pos, data)
			writeChar(CHAR_ARRAY_END)
			next()
		}

		def objectField(): Unit = {
			assert(char.contains(CHAR_QUOTE), s"object field begins with $CHAR_QUOTE, but $char")

			// field name
			string()
			whitespaces()

			// separator colon
			if (!char.contains(CHAR_COLON))
				throw MalformedJsonString("Missing ':' after object field name!", line, pos, data)
			writeChar(char.get)
			next()

			// field value
			value()
		}


		def jsonObject(): Unit = {
			assert(char.contains(CHAR_OBJECT_BEGIN), s"object begins with $CHAR_OBJECT_BEGIN")
			writeChar(CHAR_OBJECT_BEGIN)
			next()

			val before = readAheadCommasAndWhitespaces()
			if (before.contains(CHAR_COMMA) && !removeCommas) {
				throw MalformedJsonString(s"Expect quoted field name but found '$CHAR_COMMA'", line, pos, data)
			}
			// fix: remove leading comma(s)
			before filterNot (_ == CHAR_COMMA) foreach writeChar

			while (char.isDefined && !char.contains(CHAR_OBJECT_END)) {
				objectField()

				val separator = readAheadCommasAndWhitespaces()
				if (char.contains(CHAR_OBJECT_END)) {
					if (separator.contains(CHAR_COMMA) && !removeCommas) {
						throw MalformedJsonString(s"Expect '$CHAR_OBJECT_END', '$CHAR_COMMA' or '$CHAR_QUOTE'", line, pos, data)
					}
					// fix: remove trailing comma(s)
					separator filterNot (_ == CHAR_COMMA) foreach writeChar
				} else {
					if (!separator.contains(CHAR_COMMA)) {
						// fix: add missing comma
						writeChar(CHAR_COMMA)
					}
					separator foreach writeChar
				}
			}

			if (!char.contains(CHAR_OBJECT_END))
				throw MalformedJsonString(s"Expect '$CHAR_OBJECT_END', '$CHAR_COMMA' or '$CHAR_QUOTE'", line, pos, data)
			writeChar(CHAR_OBJECT_END)
			next()
		}

		def value(): Unit = {
			whitespaces()
			char foreach {
				case c if NUMBER_CHARS.contains(c) => number()
				case c if c.isLetter => symbol()
				case `CHAR_QUOTE` => string()
				case `CHAR_ARRAY_BEGIN` => jsonArray()
				case `CHAR_OBJECT_BEGIN` => jsonObject()
				//        case `CHAR_COMMA` if acceptCommaWithoutValue =>
				// ignore
				case other =>
					throw MalformedJsonString(s"Expect JSON value, but found '$other'", line, pos, data)
			}
			whitespaces()
		}


		next()
		value()
		out.result()
	}


	case class MalformedJsonString(message: String, line: Int, pos: Int, data: String)
		extends Exception(s"Malformed JSON string: $message at line=$line, pos=$pos\n$data")

}

object JSONCompatibility extends JSONCompatibility

