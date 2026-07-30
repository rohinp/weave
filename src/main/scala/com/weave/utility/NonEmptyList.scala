package com.weave.utility

case class NonEmptyList[+A](head: A, tail: List[A]):
  def toList: List[A] = head :: tail
