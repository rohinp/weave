package com.weave.core

case class Edge[S](
                    from: String,
                    to: String,
                    condition: S => Boolean
                  )

object Edge {
  def apply[S](from: String, to: String): Edge[S] = {
    new Edge(from, to, _ => true)
  }

  def apply[S](from: String, to: String, condition: S => Boolean): Edge[S] = {
    new Edge(from, to, condition)
  }

  extension [S](edge: Edge[S])
    def withCondition(condition: S => Boolean): Edge[S] =
      edge.copy(condition = condition)

}