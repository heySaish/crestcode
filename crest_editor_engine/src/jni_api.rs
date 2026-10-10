use crate::buffer::Position;
use crate::editor::EditorEngine;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, JNI_TRUE};
use jni::JNIEnv;

fn get_engine<'a>(ptr: jlong) -> &'a mut EditorEngine {
    unsafe { &mut *(ptr as *mut EditorEngine) }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeCreateEngine(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let engine = Box::new(EditorEngine::new());
    Box::into_raw(engine) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDestroyEngine(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        unsafe {
            let _ = Box::from_raw(ptr as *mut EditorEngine);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeOpenFile<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    uri: JString<'local>,
    language_id: JString<'local>,
    content: JString<'local>,
) {
    let engine = get_engine(ptr);
    let u: String = env.get_string(&uri).unwrap().into();
    let l: String = env.get_string(&language_id).unwrap().into();
    let c: String = env.get_string(&content).unwrap().into();
    engine.open_document(&u, &l, &c);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeCloseFile<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    uri: JString<'local>,
) {
    let engine = get_engine(ptr);
    let u: String = env.get_string(&uri).unwrap().into();
    engine.close_document(&u);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeInsertText<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    text: JString<'local>,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let t: String = env.get_string(&text).unwrap().into();
    let change = engine.insert_text(&t);
    let json_str = serde_json::to_string(&change).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDeleteBackspace<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let change = engine.delete_backspace();
    let json_str = serde_json::to_string(&change).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeMoveCursor<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    direction: JString<'local>,
    select: jboolean,
) {
    let engine = get_engine(ptr);
    let dir: String = env.get_string(&direction).unwrap().into();
    engine.move_cursor(&dir, select == JNI_TRUE);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeSetSelection(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    anchor_line: jint,
    anchor_char: jint,
    head_line: jint,
    head_char: jint,
) {
    let engine = get_engine(ptr);
    engine.set_selection(
        Position::new(anchor_line as usize, anchor_char as usize),
        Position::new(head_line as usize, head_char as usize),
    );
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeSelectWordAt(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    line: jint,
    col: jint,
) {
    let engine = get_engine(ptr);
    engine.select_word_at(line as usize, col as usize);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetSelectedText<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let text = engine.get_selected_text();
    env.new_string(text).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDeleteSelection<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let change = engine.delete_selection();
    let json_str = serde_json::to_string(&change).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeSelectAll(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    let engine = get_engine(ptr);
    engine.select_all();
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeUndo(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jboolean {
    let engine = get_engine(ptr);
    if engine.undo() { JNI_TRUE } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeRedo(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jboolean {
    let engine = get_engine(ptr);
    if engine.redo() { JNI_TRUE } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetRenderStateJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let state = engine.get_render_state();
    let json_str = serde_json::to_string(&state).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetHighlightSpansJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    start_line: jint,
    end_line: jint,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let spans = engine.highlight_visible_lines(start_line as usize, end_line as usize);
    let json_str = serde_json::to_string(&spans).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetContent<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let content = engine
        .get_active_document()
        .map(|d| d.buffer.get_text())
        .unwrap_or_default();
    env.new_string(content).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeLspCompletionJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let items = engine.lsp_completion().unwrap_or_default();
    let json_str = serde_json::to_string(&items).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeLspHoverJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let hover = engine.lsp_hover().ok().flatten();
    let json_str = serde_json::to_string(&hover).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeLspGotoDefinitionJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let locs = engine.lsp_goto_definition().unwrap_or_default();
    let json_str = serde_json::to_string(&locs).unwrap_or_default();
    env.new_string(json_str).unwrap()
}
