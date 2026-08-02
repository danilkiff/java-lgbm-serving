package io.github.danilkiff.lgbmserving.lgbm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Прототипы C-ABI LightGBM, поднятые через FFM.
 *
 * <p>Описаны вручную, а не сгенерированы из {@code c_api.h}: заголовок тянет
 * C++ и Arrow, а нужны шесть функций. C API - стабильная поверхность
 * {@code extern "C"}, дескрипторы ниже отвечают LightGBM 4.x, и версия колеса
 * фиксирована в {@code native/requirements.txt}.
 *
 * <p>Библиотека и строка параметров живут в {@link Arena#global()}: и то и
 * другое существует всё время работы процесса, а горячий путь не должен
 * аллоцировать.
 */
final class CApi {

    /** {@code C_API_DTYPE_FLOAT64}. */
    static final int DTYPE_FLOAT64 = 1;

    /** {@code C_API_PREDICT_RAW_SCORE} - raw margin до сигмоиды. */
    static final int PREDICT_RAW = 1;

    /** {@code C_API_PREDICT_CONTRIB} - значения SHAP. */
    static final int PREDICT_CONTRIB = 3;

    /**
     * Один нативный поток на предсказание. Порядок редукции float в
     * многопоточном OpenMP - источник недетерминизма между запусками;
     * параллелизм держим на уровне JVM через {@link Pool}.
     */
    static final MemorySegment PREDICT_PARAM;

    static final MethodHandle GET_LAST_ERROR;
    static final MethodHandle LOAD_MODEL_FROM_STRING;
    static final MethodHandle FREE;
    static final MethodHandle GET_NUM_FEATURE;
    static final MethodHandle CALC_NUM_PREDICT;
    static final MethodHandle PREDICT_FOR_MAT;

    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final java.lang.foreign.AddressLayout PTR = ValueLayout.ADDRESS;

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib = SymbolLookup.libraryLookup(NativeLibrary.path(), Arena.global());

        GET_LAST_ERROR = bind(linker, lib, "LGBM_GetLastError", FunctionDescriptor.of(PTR));
        LOAD_MODEL_FROM_STRING = bind(linker, lib, "LGBM_BoosterLoadModelFromString",
                FunctionDescriptor.of(INT, PTR, PTR, PTR));
        FREE = bind(linker, lib, "LGBM_BoosterFree", FunctionDescriptor.of(INT, PTR));
        GET_NUM_FEATURE = bind(linker, lib, "LGBM_BoosterGetNumFeature",
                FunctionDescriptor.of(INT, PTR, PTR));
        CALC_NUM_PREDICT = bind(linker, lib, "LGBM_BoosterCalcNumPredict",
                FunctionDescriptor.of(INT, PTR, INT, INT, INT, INT, PTR));
        PREDICT_FOR_MAT = bind(linker, lib, "LGBM_BoosterPredictForMat",
                FunctionDescriptor.of(INT, PTR, PTR, INT, INT, INT, INT, INT, INT, INT, PTR, PTR, PTR));

        PREDICT_PARAM = Arena.global().allocateFrom("num_threads=1");
    }

    private CApi() {}

    private static MethodHandle bind(
            Linker linker, SymbolLookup lib, String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = lib.find(name)
                .orElseThrow(() -> new IllegalStateException(
                        "нет символа " + name + " в " + NativeLibrary.path()));
        return linker.downcallHandle(symbol, descriptor);
    }

    /**
     * Текст последней ошибки нативной библиотеки. Возвращённый указатель имеет
     * нулевую длину для FFM, поэтому переинтерпретируется до чтения строки.
     */
    static String lastError() {
        try {
            MemorySegment msg = (MemorySegment) GET_LAST_ERROR.invokeExact();
            if (msg.equals(MemorySegment.NULL)) {
                return "неизвестная ошибка";
            }
            return msg.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw new LgbmException("не удалось прочитать LGBM_GetLastError", t);
        }
    }
}
