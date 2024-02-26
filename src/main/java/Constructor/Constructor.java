package Constructor;

import Constructor.Enums.FileType;
import Constructor.Enums.Operator;
import Constructor.Visitors.*;
import Model.*;
import Project.RefactoringMiner.Refactoring;
import Project.RefactoringMiner.Refactorings;
import Project.Utils.CommitHashCode;
import Project.Utils.DiffFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import Project.Project;

import java.util.*;
import java.util.stream.Collectors;

import Util.JedisUtil;
import redis.clients.jedis.Jedis;
@Data
public class Constructor {
    Jedis jedis = JedisUtil.getJedis();
    Project project;
    List<CodeBlock> codeBlocks = new ArrayList<>();
    List<CommitCodeChange> codeChange = new ArrayList<>();


    public Constructor(Project p) {
        project = p;
    }


    public void start(){

        List<CommitHashCode> commitList = project.getCommitList();
        for(CommitHashCode hashCode: commitList){
//            System.out.println(codeBlocks.size());
//            System.out.println(mappings.size());
            System.out.println("Commit: "+hashCode.getHashCode());
            //add a new commitTime for each commit, for the code change during this commit
            CommitCodeChange commitTime = new CommitCodeChange(hashCode.getHashCode());
            if(!codeChange.isEmpty()){
                commitTime.setPreCommit(codeChange.get(codeChange.size()-1));
                codeChange.get(codeChange.size()-1).setPostCommit(commitTime);
            }else{
                commitTime.setPreCommit(null);
            }
            codeChange.add(commitTime);

            //go through all the files and refactorings
            HashMap<String, DiffFile> fileList1 =  project.getDiffList(hashCode);
            if(fileList1==null){continue;}//no file changes during this commit
            Map<String, DiffFile> fileList = fileList1.entrySet().stream()
                    .filter(p -> !FileType.DELETE.equals(p.getValue().getType()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            //if refactoring is not null, separate them into three levels: package, class, method&attribute
            Refactorings refact = project.getRefactorings().get(hashCode.getHashCode());

            //生成ASTReader所需参数
            Map<String, String> fileContents = fileList.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, p -> p.getValue().getContent()));
            Set<String> repositoryDirectories = populateDirectories(fileContents);

            RefactoringParser refactoringParser = new RefactoringParser();
            Map<String, String> renameCodeBlockName;
            if (commitTime.getPreCommit() != null){
                renameCodeBlockName = refactoringParser.parse(refact);
            } else {
                renameCodeBlockName = new HashMap<>();
            }
            Visitor visitor = new Visitor();
            visitor.visit(fileContents, codeBlocks, codeChange, repositoryDirectories, fileList, renameCodeBlockName);

            try {
                //packageLevel: firstly refactorings, then javaParser visitor
                if (refact != null && commitTime.getPreCommit() != null) {
//                System.out.println("--------Package Level--------");
                    if (!refact.getRefactorings().isEmpty()) {
                        List<Refactoring> packageLevelRefactorings = refact.filter("package");
                        if (!packageLevelRefactorings.isEmpty()) {
                            for (Refactoring r : packageLevelRefactorings) {
                                Operator.valueOf(r.getType().replace(" ", "_")).apply(codeBlocks, r, commitTime, null);
                            }
                        }
                    }
                }
            }catch (Exception e){
                System.err.println("Caught Exception in packageLevel: " + e.getMessage());
            }

//            PackageVisitor packageVisitor = new PackageVisitor();
//            packageVisitor.packageVisitor(fileContents, repositoryDirectories, codeBlocks, codeChange, mappings);
            updateMappings(codeBlocks);

            try {
                //classLevel; firstly refactorings, then javaparser visitor
                if (refact != null && commitTime.getPreCommit() != null) {
//                System.out.println("--------Class Level--------");
                    if (!refact.getRefactorings().isEmpty()) {
                        // class level
                        List<Refactoring> classLevelRefactorings = refact.filter("class");
                        if (!classLevelRefactorings.isEmpty()) {
                            for (Refactoring r : classLevelRefactorings) {
                                Operator.valueOf(r.getType().replace(" ", "_")).apply(codeBlocks, r, commitTime, null);
                            }
                        }
                    }
                }
            }catch (Exception e){
                System.err.println("Caught Exception in classLevel: " + e.getMessage());
            }

//            ClassVisitor classVisitor = new ClassVisitor();
//            classVisitor.classVisitor(fileContents, repositoryDirectories, codeBlocks, codeChange, mappings);
            updateMappings(codeBlocks);
//            //method and attribute level: firstly refactoring, then javaparser visitor
//            if (refact != null && commitTime.getPreCommit() != null) {
////                System.out.println("--------MethodAndAttribute Level--------");
//                if (!refact.getRefactorings().isEmpty()) {
//                    //method & attribute
//                    List<Refactoring> methodAndAttributeLevelRefactorings = refact.filter("methodAndAttribute");
//                    if (!methodAndAttributeLevelRefactorings.isEmpty()) {
//                        for(Refactoring r: methodAndAttributeLevelRefactorings){
//                            Operator.valueOf(r.getType().replace(" ", "_")).apply(codeBlocks, mappings, r, commitTime, null);
//                        }
//                    }
//                    //parameters & return type
//                    List<Refactoring> parameterLevelRefactorings = refact.filter("parameter");
//                    if (!parameterLevelRefactorings.isEmpty()) {
//                        for(Refactoring r: parameterLevelRefactorings){
//                            Operator.valueOf(r.getType().replace(" ", "_")).apply(codeBlocks, mappings, r, commitTime, null);
//                        }
//                    }
//
//                }
//            }
////            MethodAndAttributeVisitor methodAndAttributeVisitor = new MethodAndAttributeVisitor();
////            methodAndAttributeVisitor.methodAAttributeVisitor(fileContents, repositoryDirectories, codeBlocks, codeChange, mappings, classVisitor);
////            classVisitor.processResidualClass();
//            updateMappings(mappings, codeBlocks);
        }
    }

    private void updateMappings(List<CodeBlock> codeBlocks) {
        // 对于每个codeBlock，将其最新的signature加入到mappings中
        for(CodeBlock codeBlock: codeBlocks){
            try {
                CodeBlockTime history = codeBlock.getLastHistory();
                if(history == null){
                    continue;
                }
                String signature = history.getSignature();
                ObjectMapper objectMapper = new ObjectMapper();
                String codeBlockStr = objectMapper.writeValueAsString(codeBlock);
                jedis.set(signature, codeBlockStr);
                jedis.close();
            } catch (NullPointerException e) {
                // 再次捕获和处理异常
                System.err.println("Caught NullPointerException in updateMappings: " + e.getMessage());
            } catch (JsonProcessingException e) {
                System.err.println("Caught JsonProcessingException in updateMappings: " + e.getMessage());
            }
        }
    }

    private static Set<String> populateDirectories(Map<String, String> fileContents) {
        Set<String> repositoryDirectories = new LinkedHashSet<>();
        for(String path : fileContents.keySet()) {
            String[] parts = path.split("/");
            StringBuilder directory = new StringBuilder();
            for(int i=0;i<parts.length-1;i++) {
                directory.append(parts[i]);
                repositoryDirectories.add(directory.toString());
                directory.append("/");
            }
        }
        return repositoryDirectories;
    }
}
