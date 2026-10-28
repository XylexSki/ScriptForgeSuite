package dev.scriptforge.core;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.WrapFactory;

/** Rhino configuration shared by every script. */
public final class ScriptEngine {
    private ScriptEngine() {}

    public static final ContextFactory FACTORY = new ContextFactory() {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setLanguageVersion(Context.VERSION_ES6); // let/const, arrow functions, ...
            cx.setOptimizationLevel(-1);                // interpreter mode: no class generation, reliable inside mod loaders
            cx.setInstructionObserverThreshold(10_000); // lets us kill runaway loops like while(true){}
            WrapFactory wrap = new WrapFactory();
            wrap.setJavaPrimitiveWrap(false);           // Java String/Number/Boolean come back as real JS primitives
            cx.setWrapFactory(wrap);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Object sc = cx.getThreadLocal(ScriptContext.TL_KEY);
            if (sc instanceof ScriptContext ctx && ctx.stopped) throw new ScriptStopped();
        }
    };
}
