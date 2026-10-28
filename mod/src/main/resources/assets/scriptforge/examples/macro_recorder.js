// @description Records a macro (walking, digging, placing, chat) - press F8 to stop and save it as a .sfmacro
// The file lands in scriptforge/macros/ and can be replayed here (/script play <name>) or by the headless client.
macro.record();
chat.print("&a[Macro] &fRecording. Do your thing, then press F8 to stop and save.");

events.onKey("f8", function () {
    const name = "macro_" + Date.now();
    const ticks = macro.stop(name);
    chat.print("&a[Macro] &fSaved &b" + name + ".sfmacro &f(" + ticks + " ticks)");
    exit();
});
