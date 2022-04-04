package maasertracker

import cats.implicits.toInvariantOps
import io.circe.{Codec, Decoder, Encoder}
import slick.additions.entity.{EntityKey, Lookup}

object Codecs {
  implicit def lookupCodec[K: Decoder: Encoder, A]: Codec[Lookup[K, A]] =
    Codec.from(Decoder[K], Encoder[K]).imap[Lookup[K, A]](EntityKey(_))(_.key)
}
