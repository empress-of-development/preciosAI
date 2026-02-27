// app/src/main/cpp/native-lib.cpp

#include <jni.h>
#include <vector>
#include <algorithm>
#include <cmath>
#include <cfloat>

struct Rect {
    float x;
    float y;
    float width;
    float height;
};

struct Detection {
    Rect rect;
    int index;
    float confidence;
};

static inline float intersection_area(const Rect& a, const Rect& b) {
    const float left   = std::max(a.x, b.x);
    const float top    = std::max(a.y, b.y);
    const float right  = std::min(a.x + a.width,  b.x + b.width);
    const float bottom = std::min(a.y + a.height, b.y + b.height);

    const float w = right  - left;
    const float h = bottom - top;

    if (w <= 0.0f || h <= 0.0f) return 0.0f;
    return w * h;
}

// NMS для отсортированных по confidence объектов (по убыванию)
static void nms(const std::vector<Detection>& objects,
                std::vector<int>& picked,
                float iou_threshold)
{
    picked.clear();
    const int n = static_cast<int>(objects.size());
    if (n == 0) return;

    std::vector<float> areas(n);
    for (int i = 0; i < n; ++i) {
        const Rect& r = objects[i].rect;
        areas[i] = r.width * r.height;
    }

    picked.reserve(n);

    for (int i = 0; i < n; ++i) {
        const Rect& a = objects[i].rect;
        const float areaA = areas[i];

        bool keep = true;

        for (int j : picked) {
            const Rect& b = objects[j].rect;

            const float inter = intersection_area(a, b);
            if (inter <= 0.0f) continue;

            const float uni = areaA + areas[j] - inter;
            if (uni <= 0.0f) continue;

            if (inter / uni > iou_threshold) {
                keep = false;
                break;
            }
        }

        if (keep) picked.push_back(i);
    }
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_preciosai_photo_1capture_1plugin_MMPoseEstimator_postprocess(
        JNIEnv *env,
        jobject /*thiz*/,
        jobjectArray recognitions,
        jint w, jint h,
        jfloat confidence_threshold,
        jfloat iou_threshold,
        jint num_items_threshold,
        jint num_classes) {

    if (!recognitions || w <= 0 || h <= 0 || num_classes <= 0) return nullptr;
    if (h < 4 + num_classes) return nullptr;

    const jsize rows = env->GetArrayLength(recognitions);
    if (rows < 4 + num_classes) return nullptr;

    jfloatArray row_cx = (jfloatArray)env->GetObjectArrayElement(recognitions, 0);
    jfloatArray row_cy = (jfloatArray)env->GetObjectArrayElement(recognitions, 1);
    jfloatArray row_bw = (jfloatArray)env->GetObjectArrayElement(recognitions, 2);
    jfloatArray row_bh = (jfloatArray)env->GetObjectArrayElement(recognitions, 3);

    if (!row_cx || !row_cy || !row_bw || !row_bh) {
        if (row_cx) env->DeleteLocalRef(row_cx);
        if (row_cy) env->DeleteLocalRef(row_cy);
        if (row_bw) env->DeleteLocalRef(row_bw);
        if (row_bh) env->DeleteLocalRef(row_bh);
        return nullptr;
    }

    const jsize len0 = env->GetArrayLength(row_cx);
    const jsize len1 = env->GetArrayLength(row_cy);
    const jsize len2 = env->GetArrayLength(row_bw);
    const jsize len3 = env->GetArrayLength(row_bh);
    if (len0 < w || len1 < w || len2 < w || len3 < w) {
        env->DeleteLocalRef(row_cx);
        env->DeleteLocalRef(row_cy);
        env->DeleteLocalRef(row_bw);
        env->DeleteLocalRef(row_bh);
        return nullptr;
    }

    jboolean isCopy = JNI_FALSE;
    jfloat* cx = env->GetFloatArrayElements(row_cx, &isCopy);
    jfloat* cy = env->GetFloatArrayElements(row_cy, &isCopy);
    jfloat* bw = env->GetFloatArrayElements(row_bw, &isCopy);
    jfloat* bh = env->GetFloatArrayElements(row_bh, &isCopy);

    if (!cx || !cy || !bw || !bh) {
        if (cx) env->ReleaseFloatArrayElements(row_cx, cx, JNI_ABORT);
        if (cy) env->ReleaseFloatArrayElements(row_cy, cy, JNI_ABORT);
        if (bw) env->ReleaseFloatArrayElements(row_bw, bw, JNI_ABORT);
        if (bh) env->ReleaseFloatArrayElements(row_bh, bh, JNI_ABORT);
        env->DeleteLocalRef(row_cx);
        env->DeleteLocalRef(row_cy);
        env->DeleteLocalRef(row_bw);
        env->DeleteLocalRef(row_bh);
        return nullptr;
    }

    std::vector<jfloatArray> classRows(num_classes, nullptr);
    std::vector<jfloat*> classPtrs(num_classes, nullptr);

    for (int c = 0; c < num_classes; ++c) {
        jfloatArray row = (jfloatArray)env->GetObjectArrayElement(recognitions, 4 + c);
        if (!row || env->GetArrayLength(row) < w) {
            for (int k = 0; k <= c; ++k) {
                if (classPtrs[k]) env->ReleaseFloatArrayElements(classRows[k], classPtrs[k], JNI_ABORT);
                if (classRows[k]) env->DeleteLocalRef(classRows[k]);
            }
            env->ReleaseFloatArrayElements(row_cx, cx, JNI_ABORT);
            env->ReleaseFloatArrayElements(row_cy, cy, JNI_ABORT);
            env->ReleaseFloatArrayElements(row_bw, bw, JNI_ABORT);
            env->ReleaseFloatArrayElements(row_bh, bh, JNI_ABORT);
            env->DeleteLocalRef(row_cx);
            env->DeleteLocalRef(row_cy);
            env->DeleteLocalRef(row_bw);
            env->DeleteLocalRef(row_bh);
            return nullptr;
        }
        classRows[c] = row;
        classPtrs[c] = env->GetFloatArrayElements(row, &isCopy);
        if (!classPtrs[c]) {
            for (int k = 0; k <= c; ++k) {
                if (classPtrs[k]) env->ReleaseFloatArrayElements(classRows[k], classPtrs[k], JNI_ABORT);
                if (classRows[k]) env->DeleteLocalRef(classRows[k]);
            }
            env->ReleaseFloatArrayElements(row_cx, cx, JNI_ABORT);
            env->ReleaseFloatArrayElements(row_cy, cy, JNI_ABORT);
            env->ReleaseFloatArrayElements(row_bw, bw, JNI_ABORT);
            env->ReleaseFloatArrayElements(row_bh, bh, JNI_ABORT);
            env->DeleteLocalRef(row_cx);
            env->DeleteLocalRef(row_cy);
            env->DeleteLocalRef(row_bw);
            env->DeleteLocalRef(row_bh);
            return nullptr;
        }
    }

    std::vector<Detection> proposals;
    proposals.reserve(static_cast<size_t>(w));

    for (int i = 0; i < w; ++i) {
        int bestClass = 0;
        float bestScore = -FLT_MAX;

        for (int c = 0; c < num_classes; ++c) {
            const float s = classPtrs[c][i];
            if (s > bestScore) {
                bestScore = s;
                bestClass = c;
            }
        }

        if (bestScore > confidence_threshold) {
            const float w_box = bw[i];
            const float h_box = bh[i];

            Detection obj;
            obj.rect.x = cx[i] - 0.5f * w_box;
            obj.rect.y = cy[i] - 0.5f * h_box;
            obj.rect.width = w_box;
            obj.rect.height = h_box;
            obj.index = bestClass;
            obj.confidence = bestScore;

            proposals.push_back(obj);
        }
    }

    // Освобождаем входные массивы как можно раньше
    for (int c = 0; c < num_classes; ++c) {
        env->ReleaseFloatArrayElements(classRows[c], classPtrs[c], JNI_ABORT);
        env->DeleteLocalRef(classRows[c]);
    }

    env->ReleaseFloatArrayElements(row_cx, cx, JNI_ABORT);
    env->ReleaseFloatArrayElements(row_cy, cy, JNI_ABORT);
    env->ReleaseFloatArrayElements(row_bw, bw, JNI_ABORT);
    env->ReleaseFloatArrayElements(row_bh, bh, JNI_ABORT);

    env->DeleteLocalRef(row_cx);
    env->DeleteLocalRef(row_cy);
    env->DeleteLocalRef(row_bw);
    env->DeleteLocalRef(row_bh);

    // Сортировка по confidence (убывание)
    std::sort(proposals.begin(), proposals.end(),
              [](const Detection& a, const Detection& b) {
                  return a.confidence > b.confidence;
              });

    // NMS
    std::vector<int> picked;
    nms(proposals, picked, iou_threshold);

    const int maxCount = std::max(0, static_cast<int>(num_items_threshold));
    const int count = std::min(static_cast<int>(picked.size()), maxCount);

    // Подготовка JNI-результата
    jclass floatArrayCls = env->FindClass("[F");
    if (!floatArrayCls) return nullptr;

    jobjectArray out = env->NewObjectArray(count, floatArrayCls, nullptr);
    if (!out) return nullptr;

    for (int k = 0; k < count; ++k) {
        const Detection& o = proposals[picked[k]];
        jfloat box[6] = {
                o.rect.x,
                o.rect.y,
                o.rect.width,
                o.rect.height,
                o.confidence,
                static_cast<jfloat>(o.index)
        };

        jfloatArray arr = env->NewFloatArray(6);
        if (!arr) return nullptr;

        env->SetFloatArrayRegion(arr, 0, 6, box);
        env->SetObjectArrayElement(out, k, arr);
        env->DeleteLocalRef(arr);
    }

    return out;
}
