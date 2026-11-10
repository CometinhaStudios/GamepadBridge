from pathlib import Path

profile = Path("app/src/main/java/com/gamepadbridge/app/data/ControllerProfile.kt")
s = profile.read_text()
old = '''    LS_X("Analógico Esquerdo — Eixo X", isAxis = true),
    LS_Y("Analógico Esquerdo — Eixo Y", isAxis = true),
    RS_X("Analógico Direito — Eixo X", isAxis = true),
    RS_Y("Analógico Direito — Eixo Y", isAxis = true);'''
new = '''    LS_X("Analógico Esquerdo — Eixo X (legado)", isAxis = true),
    LS_Y("Analógico Esquerdo — Eixo Y (legado)", isAxis = true),
    RS_X("Analógico Direito — Eixo X (legado)", isAxis = true),
    RS_Y("Analógico Direito — Eixo Y (legado)", isAxis = true),
    LS_LEFT("Analógico esquerdo — mover para ESQUERDA", isAxis = true),
    LS_RIGHT("Analógico esquerdo — mover para DIREITA", isAxis = true),
    LS_UP("Analógico esquerdo — mover para CIMA", isAxis = true),
    LS_DOWN("Analógico esquerdo — mover para BAIXO", isAxis = true),
    RS_LEFT("Analógico direito — mover para ESQUERDA", isAxis = true),
    RS_RIGHT("Analógico direito — mover para DIREITA", isAxis = true),
    RS_UP("Analógico direito — mover para CIMA", isAxis = true),
    RS_DOWN("Analógico direito — mover para BAIXO", isAxis = true);'''
if old not in s: raise SystemExit("enum axes block not found")
s=s.replace(old,new)
old='''            L2, R2, LS_X, LS_Y, RS_X, RS_Y
        )'''
new='''            L2, R2,
            LS_LEFT, LS_RIGHT, LS_UP, LS_DOWN,
            RS_LEFT, RS_RIGHT, RS_UP, RS_DOWN
        )'''
if old not in s: raise SystemExit("mapping order block not found")
s=s.replace(old,new)
old='''            XboxInput.LS_X -> MotionEvent.AXIS_X
            XboxInput.LS_Y -> MotionEvent.AXIS_Y
            XboxInput.RS_X -> MotionEvent.AXIS_Z
            XboxInput.RS_Y -> MotionEvent.AXIS_RZ'''
new='''            XboxInput.LS_X, XboxInput.LS_LEFT, XboxInput.LS_RIGHT -> MotionEvent.AXIS_X
            XboxInput.LS_Y, XboxInput.LS_UP, XboxInput.LS_DOWN -> MotionEvent.AXIS_Y
            XboxInput.RS_X, XboxInput.RS_LEFT, XboxInput.RS_RIGHT -> MotionEvent.AXIS_Z
            XboxInput.RS_Y, XboxInput.RS_UP, XboxInput.RS_DOWN -> MotionEvent.AXIS_RZ'''
if old not in s: raise SystemExit("output axis block not found")
s=s.replace(old,new)
# Accept controllers whose L2/R2 are reported as digital key events as well as axes.
if "XboxInput.L2 -> KeyEvent.KEYCODE_BUTTON_L2" not in s:
    s = s.replace(
        "XboxInput.R1 -> KeyEvent.KEYCODE_BUTTON_R1",
        "XboxInput.R1 -> KeyEvent.KEYCODE_BUTTON_R1" + chr(10) +
        "            XboxInput.L2 -> KeyEvent.KEYCODE_BUTTON_L2" + chr(10) +
        "            XboxInput.R2 -> KeyEvent.KEYCODE_BUTTON_R2"
    )
profile.write_text(s)

visual = Path("app/src/main/java/com/gamepadbridge/app/ui/XboxControllerVisual.kt")
s = visual.read_text()
s=s.replace('''focus(setOf(XboxInput.LS_X, XboxInput.LS_Y, XboxInput.L3), 320f, 325f, 62f)''','''focus(setOf(XboxInput.LS_X, XboxInput.LS_Y, XboxInput.LS_LEFT, XboxInput.LS_RIGHT, XboxInput.LS_UP, XboxInput.LS_DOWN, XboxInput.L3), 320f, 325f, 62f)''')
s=s.replace('''focus(setOf(XboxInput.RS_X, XboxInput.RS_Y, XboxInput.R3), 690f, 382f, 62f)''','''focus(setOf(XboxInput.RS_X, XboxInput.RS_Y, XboxInput.RS_LEFT, XboxInput.RS_RIGHT, XboxInput.RS_UP, XboxInput.RS_DOWN, XboxInput.R3), 690f, 382f, 62f)''')
visual.write_text(s)
print("Applied directional analog mapping and multi-direction axis handling.")
