package Project.Utils;
import Constructor.Enums.FileType;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.abs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Data
public class DiffFile {
    FileType type;// add, delete, rename, modify, derive
    String path;
    String content;
    String oldPath;
    String oldContent;
    String patch;
    List<Integer> codeChangeLineNum;
    List<Integer> oldCodeChangeLineNum;

    private static final Logger logger = LoggerFactory.getLogger(DiffFile.class);

    public DiffFile(FileType type, String path, String content){//仅用于项目初始状态 全部都是add
        this.type = type;
        this.path = path;
        this.content = content;
        this.oldPath = null;
        this.oldContent = null;
    }
    public DiffFile(FileType type, String path, String content, String oldPath, String oldContent){
        this.type = type;
        this.path = path;
        this.content = content;
        this.oldPath = oldPath;
        this.oldContent = oldContent;
    }

    public void patchParser(){
        this.oldCodeChangeLineNum = getLineNum('-');
        this.codeChangeLineNum = getLineNum('+');
    }

    private List<Integer> getLineNum(char separator) {
        List<Integer> resultLines = new ArrayList<Integer>(); // line number of deleted lines (the left line number of commit)<d1, d2, d3>
        try {
            patch = patch.replace("* @@", "");// this is for some special cases in bcel
            patch = patch.replace("#@@", "");// this is for codec
            String[] blocks = patch.split("@@"); //split the patch into blocks according to @@
            for (int i = 0; i < (blocks.length - 1) / 2; i++) { //different blocks of code change
                Integer startLine = 0;
                Integer currentLine;
                String firstLine = blocks[i * 2 + 1]; //numbers
                String[] lines = blocks[i * 2 + 2].split("\\n"); // code changes, split change block into lines
                String[] numbers = firstLine.split("\\s+");
                for (String n : numbers) {
                    if (n.startsWith(Character.toString(separator))) {
                        startLine = Integer.valueOf(n.split(",")[0]);
                    }
                } //obtain the number of start line and total lines

                currentLine = abs(startLine);
                for (int j = 1; j < lines.length; j++) {
                    if (!lines[j].startsWith("-") && !lines[j].startsWith("+")) {
                        currentLine = currentLine + 1;
                    } else if (lines[j].startsWith(Character.toString(separator))) {
                        resultLines.add(currentLine.intValue());
                        currentLine = currentLine + 1;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing patch: " + patch, e);
        }
        return resultLines;
    }

    public boolean containsChangeLine(int line){
        return oldCodeChangeLineNum.contains(line) || codeChangeLineNum.contains(line);
    }
}
