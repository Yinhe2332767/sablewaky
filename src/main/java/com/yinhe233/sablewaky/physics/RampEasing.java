package com.yinhe233.sablewaky.physics;

/**
 * 渐入曲线：把归一化时间 t ∈ [0,1] 映射成倍率 α ∈ [0,1]。
 */
public enum RampEasing {

    /** 线性。 */
    LINEAR {
        @Override
        public double apply(final double t) {
            return t;
        }
    },

    /** 3t² - 2t³：起点与终点导数为 0，起步最柔和（推荐默认）。 */
    SMOOTHSTEP {
        @Override
        public double apply(final double t) {
            return t * t * (3.0D - 2.0D * t);
        }
    },

    /** 1 - cos(πt/2)：正弦上升。 */
    SINE_IN {
        @Override
        public double apply(final double t) {
            return 1.0D - Math.cos(t * Math.PI * 0.5D);
        }
    },

    /** Logistic growth */
    LOGISTICS {
        @Override 
        public double apply(final double t) {
            return 1.0D / (1.0D + Math.exp(-8*(t-0.5D)));
        }
    };

    public abstract double apply(double t);
}
