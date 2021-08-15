package io.chrisdavenport.npmpackage

import io.circe._

private[npmpackage] case class TupledMap[A](key: String, value: A){
  def tupled: (String, A) = (key, value)
}
private[npmpackage] object TupledMap {
  def fromTuple[A](t: (String, A)): TupledMap[A] = TupledMap(t._1, t._2)
  implicit def encoder[A: Encoder] = Encoder.instance[TupledMap[A]]( t => 
    Json.obj(
      t.key -> Encoder[A].apply(t.value)
    )
  )
  implicit def decoder[A: Decoder] = Decoder.instance[TupledMap[A]]{h => 
    val key = h.keys.get.head
    h.downField(key).as[A].map(a => TupledMap(key, a))
  }
}