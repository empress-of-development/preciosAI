package com.preciosai.photo_capture_plugin

import android.graphics.RectF
import org.opencv.core.Size
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class InstanceObj(
    @Serializable(with = OpenCvSizeSerializer::class)
    val imageShape: Size,
    val objects: List<PredictionObj> = emptyList(),
    val speed: Double,
    val fps: Double? = null,

    var offsetLeft: Float = 0f,
    var offsetTop: Float = 0f,
    @Serializable(with = OpenCvSizeSerializer::class)
    var imageShapeCorrected: Size? = null
)

@Serializable
data class PredictionObj(
    val bbox: BBox,
    val keypoints: Keypoints,
    val label: String,
    val score: Float
)

@Serializable
data class BBox(
    var clsIndex: Int,
    var label: String,
    var score: Float,
    @Serializable(with = RectFAsArraySerializer::class)
    val xywh: RectF,
    @Serializable(with = RectFAsArraySerializer::class)
    val xywhn: RectF,
    @Serializable(with = RectFAsArraySerializer::class)
    var xywhnCorrected: RectF? = null
)

@Serializable
data class Keypoints(
    @Serializable(with = FloatPairListSerializer::class)
    var xyn: List<Pair<Float, Float>>,
    @Serializable(with = FloatPairListSerializer::class)
    var xy: List<Pair<Float, Float>>,
    val zn: List<Float>? = null,
    var scores: List<Float>,

    var xynCorrected: List<Pair<Float, Float>>? = null
)


object RectFAsArraySerializer : KSerializer<RectF> {
    private val floats = ListSerializer(Float.serializer())

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("android.graphics.RectFArray") {
            element<Float>("left")
            element<Float>("top")
            element<Float>("right")
            element<Float>("bottom")
        }

    override fun serialize(encoder: Encoder, value: RectF) {
        encoder.encodeSerializableValue(floats, listOf(value.left, value.top, value.right, value.bottom))
    }

    override fun deserialize(decoder: Decoder): RectF {
        val list = decoder.decodeSerializableValue(floats)
        require(list.size == 4) { "RectF array must have 4 floats: [left, top, right, bottom]" }
        return RectF(list[0], list[1], list[2], list[3])
    }
}

object OpenCvSizeSerializer : KSerializer<Size> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("org.opencv.core.Size") {
            element<Double>("width")
            element<Double>("height")
        }

    override fun serialize(encoder: Encoder, value: Size) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.width)
            encodeDoubleElement(descriptor, 1, value.height)
        }
    }

    override fun deserialize(decoder: Decoder): Size {
        var w = 0.0
        var h = 0.0
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    0 -> w = decodeDoubleElement(descriptor, 0)
                    1 -> h = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $i")
                }
            }
        }
        return Size(w, h)
    }
}

object FloatPairListSerializer :
    KSerializer<List<Pair<Float, Float>>> {

    private val delegate =
        ListSerializer(FloatPairAsArraySerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<Pair<Float, Float>>
    ) {
        encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): List<Pair<Float, Float>> {
        return decoder.decodeSerializableValue(delegate)
    }
}

object FloatPairAsArraySerializer : KSerializer<Pair<Float, Float>> {

    private val floats = ListSerializer(Float.serializer())

    override val descriptor: SerialDescriptor = floats.descriptor

    override fun serialize(encoder: Encoder, value: Pair<Float, Float>) {
        encoder.encodeSerializableValue(
            floats,
            listOf(value.first, value.second)
        )
    }

    override fun deserialize(decoder: Decoder): Pair<Float, Float> {
        val list = decoder.decodeSerializableValue(floats)
        require(list.size == 2) { "Pair<Float, Float> must be [x, y]" }
        return list[0] to list[1]
    }
}
