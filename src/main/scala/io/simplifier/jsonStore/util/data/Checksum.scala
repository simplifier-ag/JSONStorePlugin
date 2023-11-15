package io.simplifier.jsonStore.util.data

import java.security.MessageDigest

/**
  * MD5 Checksum generation.
  */
trait Checksum {

  /**
    * Create MD5 checksum of a byte array.
    * @param data byte array to get checksum for
    * @return checksum as Hex-String
    */
  def checksum(data: Array[Byte]): String = checksumMD5(data)

  /**
    * Create MD5 checksum of a byte array.
    * @param data byte array to get checksum for
    * @return checksum as Hex-String
    */
  def checksumMD5(data: Array[Byte]): String = checksum(data, MessageDigest.getInstance("MD5"))

  /**
    * Create SHA-256 checksum of a byte array.
    * @param data byte array to get checksum for
    * @return checksum as Hex-String
    */
  def checksumSHA256(data: Array[Byte]): String = checksum(data, MessageDigest.getInstance("SHA-256"))

  /**
    * Create checksum of a byte array and selected message digest algorithm.
    * @param data byte array to get checksum for
    * @param md message digest algorithm to use
    * @return checksum as Hex-String
    */
  def checksum(data: Array[Byte], md: MessageDigest): String = {
    md.update(data)
    val digest = md.digest()
    val sb = new StringBuffer()
    for(b <- digest)
      sb.append("%02x".format(b & 0xff))
    sb.toString.toUpperCase
  }

}

object Checksum extends Checksum
