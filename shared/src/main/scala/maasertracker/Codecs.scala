package maasertracker

import slick.additions.entity.{EntityKey, KeyedEntity, Lookup}

import cats.implicits.toInvariantOps
import io.circe.*

object Codecs {
  implicit def lookupCodec[K: Decoder: Encoder, A]: Codec[Lookup[K, A]] =
    Codec.from(Decoder[K], Encoder[K]).imap[Lookup[K, A]](EntityKey(_))(_.key)

  implicit def encodeEntityKey[K: Encoder, A]: Encoder[EntityKey[K, A]] = Encoder[K].contramap(_.key)
  implicit def decodeEntityKey[K: Decoder, A]: Decoder[EntityKey[K, A]] = Decoder[K].map(EntityKey(_))

  implicit def encodeKeyedEntity[K: Encoder, A: Encoder]: Encoder[KeyedEntity[K, A]] =
    Encoder.forProduct2("key", "value")(e => (e.key, e.value))

  implicit def decodeKeyedEntity[K: Decoder, A: Decoder]: Decoder[KeyedEntity[K, A]] =
    Decoder.forProduct2("key", "value")(KeyedEntity[K, A])

  implicit def encodeMaybeKindKey: KeyEncoder[Option[Kind]] = KeyEncoder.instance(_.map(_.name).getOrElse("-"))

  implicit def decodeMaybeKindKey: KeyDecoder[Option[Kind]] =
    KeyDecoder.instance {
      case "-" => Some(None)
      case s   => Kind.withName(s).map(Some(_))
    }
}
