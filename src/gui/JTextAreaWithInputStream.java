package Gui;

import javax.swing.JTextArea;
import java.io.OutputStream;

/** 把异常栈输出重定向到界面文本区。 */
class JTextAreaWithInputStream extends OutputStream {

    private final JTextArea textArea;

    JTextAreaWithInputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(int b) {
        if (b == '\r') {
            return;
        }
        textArea.append(String.valueOf((char) b));
    }
}
