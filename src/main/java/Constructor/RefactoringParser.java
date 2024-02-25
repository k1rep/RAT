package Constructor;

import Constructor.Enums.CodeBlockType;
import Constructor.Enums.Operator;
import Model.*;
import Project.RefactoringMiner.Refactoring;
import Project.RefactoringMiner.Refactorings;
import Project.RefactoringMiner.SideLocation;
import Util.JedisUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static Constructor.Utils.*;
import static Constructor.Utils.defaultPackage;

public class RefactoringParser {
    public Map<String, String> parse(Refactorings refactorings){
        Map<String, String> renameCodeBlockName = new HashMap<>();
        if(refactorings == null){
            return renameCodeBlockName;
        }

        List<Refactoring> refactoringList = refactorings.getRefactorings();
        for(Refactoring r: refactoringList){
            RenameOperator.valueOf(r.getType().replace(" ", "_")).apply(renameCodeBlockName, r);
        }
        return renameCodeBlockName;
    }
}

enum RenameOperator {
    Rename_Package {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            Jedis jedis = JedisUtil.getJedis();
            String[] des = r.getDescription().split(" ");
            String oldPkgName = des[2];
            String newPkgName = des[4];
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            if (!jedis.exists(oldPkgName)) {
                String firstClassName = left.get(0).getCodeElement();
                oldPkgName = firstClassName.substring(0, firstClassName.lastIndexOf("."));
            }
            renameCodeBlockName.put(newPkgName, oldPkgName);
            jedis.close();
            assert left.size() == right.size();
            for (int i = 0; i < left.size(); i++) {
                String oldClassSig = left.get(i).getCodeElement();
                String newClassSig = right.get(i).getCodeElement();
                renameCodeBlockName.put(newClassSig, oldClassSig);
            }
        }
    },
    Move_Package {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();

            for (int i = 0; i < left.size(); i++) {
                String oldSig = left.get(i).getCodeElement();
                String newSig = right.get(i).getCodeElement();
                renameCodeBlockName.put(newSig, oldSig);
            }
        }
    },
    Split_Package {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            //todo 没有了package的继承关系
            //左右一个一个对照着来
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            for (int i = 0; i < left.size(); i++) {
                String oldClassSig = left.get(i).getCodeElement();
                String newClassSig = right.get(i).getCodeElement();
                renameCodeBlockName.put(newClassSig, oldClassSig);
            }
        }
    },
    Merge_Package {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            for (int i = 0; i < left.size(); i++) {
                String oldClassSig = left.get(i).getCodeElement();
                String newClassSig = right.get(i).getCodeElement();
                renameCodeBlockName.put(newClassSig, oldClassSig);
            }
        }
    },
    Change_Type_Declaration_Kind {

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            assert r.getLeftSideLocations().size() == r.getRightSideLocations().size();
            assert r.getLeftSideLocations().size() == 1;
            String nameOld = r.getLeftSideLocations().get(0).getCodeElement();
            String nameNew = r.getRightSideLocations().get(0).getCodeElement();
            if (nameOld.equals(nameNew)) {
                return;//nothing changes, return
            }
            renameCodeBlockName.put(nameNew, nameOld);
        }
    },
    Collapse_Hierarchy {

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Extract_Superclass {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Extract_Interface {
        //done 文件重命名 这个有点特殊
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            //左边、右边前几个分别是original类的声明，右边最后一个是新的interface
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();

            for (int i = 0; i < left.size(); i++) {
                String oldName = left.get(i).getCodeElement();
                String newName = right.get(i).getCodeElement();
                renameCodeBlockName.put(newName, oldName);
            }
        }
    },
    Extract_Class {

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Extract_Subclass {
        @Override
        // 跟extract class几乎差不多
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == 1;
            String newSig = right.get(1).getCodeElement();
            String originSigOld = left.get(0).getCodeElement();

            assert r.rightFilter("extracted type declaration").size() == 1;//假设右侧只有一个类，如果有多个的话，就说明是有内部类
            List<SideLocation> extractedMethod = r.rightFilter("extracted method declaration");
            for (SideLocation s : extractedMethod) {
                HashMap<String, String> methodInfo = s.parseMethodDeclaration();
                renameCodeBlockName.put(newSig + ":" + methodInfo.get("MN"), originSigOld + ":" + methodInfo.get("MN"));
            }
        }
    },
    Merge_Class {//merge methods & attributes in two or more classes to one new class

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r){
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert right.size() == 1;
            String newSig = right.get(0).getCodeElement();
            Jedis jedis = JedisUtil.getJedis();
            for (int i = 0; i < left.size(); i++) {
                String oldSig = left.get(i).getCodeElement();
                assert jedis.exists(oldSig);
                String oldClassStr = jedis.get(oldSig);
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    CodeBlock oldClassBlock = objectMapper.readValue(oldClassStr, CodeBlock.class);
                    ClassTime oldClassTime = (ClassTime) oldClassBlock.getLastHistory();

                    //change sons' parentBlock
                    if (!(oldClassTime.getClasses() == null)) {
                        for (CodeBlock sonBlock : oldClassTime.getClasses()) {
                            renameCodeBlockName.put(sonBlock.getLastHistory().getSignature().replace(oldSig, newSig), sonBlock.getLastHistory().getSignature());
                        }
                    }
                    if (!(oldClassTime.getMethods() == null)) {
                        for (CodeBlock sonBlock : oldClassTime.getMethods()) {
                            renameCodeBlockName.put(sonBlock.getLastHistory().getSignature().replace(oldSig, newSig), sonBlock.getLastHistory().getSignature());
                        }
                    }
                    if (!(oldClassTime.getAttributes() == null)) {
                        for (CodeBlock sonBlock : oldClassTime.getAttributes()) {
                            renameCodeBlockName.put(sonBlock.getLastHistory().getSignature().replace(oldSig, newSig), sonBlock.getLastHistory().getSignature());
                        }
                    }
                }catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
            jedis.close();
        }
    },
    Move_Class {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            //update class, class.father, class.son
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;
            String oldSig = left.get(0).getCodeElement();
            String newSig = right.get(0).getCodeElement();

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Rename_Class {
        @Override
        // update class name, update mappings of methods, attributes, etc.
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;
            System.out.println(r.getDescription());
            String oldName = left.get(0).getCodeElement();
            String newName = right.get(0).getCodeElement();
            oldName = defaultPackage(oldName);
            newName = defaultPackage(newName);
            Jedis jedis = JedisUtil.getJedis();
            assert jedis.exists(oldName);
            renameCodeBlockName.put(newName, oldName);
            jedis.close();
        }
    },
    Move_And_Rename_Class {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;
            String oldSig = left.get(0).getCodeElement();
            String newSig = right.get(0).getCodeElement();

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Extract_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            HashMap<String, String> oldMethod = r.getLeftSideLocations().get(0).parseMethodDeclaration();//parse the method name
            HashMap<String, String> oldMethodNew;

            oldMethodNew = r.rightFilter("source method declaration after extraction").get(0).parseMethodDeclaration();
            if (!oldMethod.get("MN").equals(oldMethodNew.get("MN"))) {
                renameCodeBlockName.put(className + ":" + oldMethodNew.get("MN"), className + ":" + oldMethod.get("MN"));
            };
        }
    },
    Inline_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            HashMap<String, String> methodNameOld = r.getLeftSideLocations().get(r.getRightSideLocations().size()).parseMethodDeclaration();
            HashMap<String, String> methodNameNew = r.getRightSideLocations().get(0).parseMethodDeclaration();

            if (!methodNameNew.get("MN").equals(methodNameOld.get("MN"))) {
                renameCodeBlockName.put(className + ":" + methodNameNew.get("MN"), className + ":" + methodNameOld.get("MN"));
            }
        }
    },
    Pull_Up_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClass = defaultPackage(r.getFirstClassName());
            String newClass = defaultPackage(r.getLastClassName());

            assert r.getLeftSideLocations().size() == r.getRightSideLocations().size();
            assert r.getRightSideLocations().size() == 1;
            HashMap<String, String> oldMethod = r.getLeftSideLocations().get(0).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = r.getRightSideLocations().get(0).parseMethodDeclaration();

            Jedis jedis = JedisUtil.getJedis();
            assert jedis.exists(oldClass + ":" + oldMethod.get("MN"));
            assert jedis.exists(oldClass);
            renameCodeBlockName.put(newClass + ":" + newMethod.get("MN"), oldClass + ":" + oldMethod.get("MN"));
            jedis.close();
        }
    },
    Push_Down_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Extract_And_Move_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClassName = defaultPackage(r.getFirstClassName());
            assert oldClassName.contains(".");
            HashMap<String, String> oldMethod = r.getLeftSideLocations().get(0).parseMethodDeclaration();//parse the method name
            HashMap<String, String> oldMethodNew = r.getRightSideLocations().get(r.getLeftSideLocations().size()).parseMethodDeclaration();

            if (!oldMethod.get("MN").equals(oldMethodNew.get("MN"))) {
                renameCodeBlockName.put(oldClassName + ":" + oldMethodNew.get("MN"), oldClassName + ":" + oldMethod.get("MN"));
            }
        }
    },
    Move_And_Inline_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClassName = r.getFirstClassName();
            String[] tmp = r.getDescription().substring(0, r.getDescription().indexOf(" & ")).split(" ");
            String newClassName = tmp[tmp.length - 1];
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();

            HashMap<String, String> inlinedMethodInfo = left.get(0).parseMethodDeclaration();//parse the method name
            HashMap<String, String> targetMethodInfoOld = left.get(right.size()).parseMethodDeclaration();
            HashMap<String, String> targetMethodInfoNew = right.get(0).parseMethodDeclaration();

            renameCodeBlockName.put(newClassName + ":" + inlinedMethodInfo.get("MN"), oldClassName + ":" + inlinedMethodInfo.get("MN"));
            renameCodeBlockName.put(newClassName + ":" + targetMethodInfoNew.get("MN"), newClassName + ":" + targetMethodInfoOld.get("MN"));

        }
    },
    Move_And_Rename_Method {//跨文件

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClass = defaultPackage(r.getFirstClassName());
            String newClass = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert oldClass.contains(".");
            assert newClass.contains(".");
            assert left.size() == right.size();
            assert left.size() == 1;
            HashMap<String, String> oldMethod = left.get(0).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(0).parseMethodDeclaration();
            String oldSig = oldClass + ":" + oldMethod.get("MN");
            String newSig = newClass + ":" + newMethod.get("MN");

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Move_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            //find method, oldclass and newclass from mappings, change parentBlock from oldclass to newClass
            String oldClass = defaultPackage(r.getFirstClassName());
            String newClass = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert oldClass.contains(".");
            assert newClass.contains(".");
            assert left.size() == right.size();
            assert left.size() == 1;
            HashMap<String, String> oldMethod = left.get(0).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(0).parseMethodDeclaration();
            String oldSig = oldClass + ":" + oldMethod.get("MN");
            String newSig = newClass + ":" + newMethod.get("MN");

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Change_Return_Type { //trival 只需要修改返回类型 一般不跨文件

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 2;
            HashMap<String, String> oldMethod = left.get(1).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(1).parseMethodDeclaration();
            String newSig = className + ":" + newMethod.get("MN");
            renameCodeBlockName.put(newSig, className + ":" + oldMethod.get("MN"));
        }
    },
    Rename_Method {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;

            HashMap<String, String> oldMethod = left.get(0).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(0).parseMethodDeclaration();
            String oldSig = className + ":" + oldMethod.get("MN");
            String newSig = className + ":" + newMethod.get("MN");

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Parameterize_Variable {//方法名级别

        @Override
        //把方法中的一个变量 变为方法的参数 不跨文件，仅需要修改方法的名字
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 2;
            HashMap<String, String> oldMethod = left.get(1).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(1).parseMethodDeclaration();
            String oldSig = className + ":" + oldMethod.get("MN");
            String newSig = className + ":" + newMethod.get("MN");

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Merge_Parameter {//把一个方法的参数进行合并，但是可能会有移动 左右两边的最后一个 分别是旧新方法的声明

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            HashMap<String, String> oldMethod = left.get(left.size() - 1).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(right.size() - 1).parseMethodDeclaration();
            String oldSig = className + ":" + oldMethod.get("MN");
            String newSig = className + ":" + newMethod.get("MN");

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Split_Parameter {//method name change, parameterList change 左右两边的最后一个分别是旧、新方法的声明

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            HashMap<String, String> oldMethod = left.get(left.size() - 1).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(right.size() - 1).parseMethodDeclaration();
            String oldSig = className + ":" + oldMethod.get("MN");
            String newSig = className + ":" + newMethod.get("MN");
            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Change_Parameter_Type {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            System.out.println(r.getDescription());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert right.size() == 2;
            assert left.size() == 2;
            HashMap<String, String> oldMethod = left.get(1).parseMethodDeclaration();
            HashMap<String, String> newMethod = right.get(1).parseMethodDeclaration();

            renameCodeBlockName.put(className + ":" + newMethod.get("MN"), className + ":" + oldMethod.get("MN"));
        }
    },
    Add_Parameter {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert right.size() == 2;
            assert left.size() == 1;
            HashMap<String, String> oldMethod = left.get(0).parseMethodDeclaration();
            HashMap<String, String> newMethod = right.get(1).parseMethodDeclaration();
            renameCodeBlockName.put(className + ":" + newMethod.get("MN"), className + ":" + oldMethod.get("MN"));
        }
    },
    Remove_Parameter {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert right.size() == 1;
            assert left.size() == 2;
            HashMap<String, String> oldMethod = left.get(1).parseMethodDeclaration();
            HashMap<String, String> newMethod = right.get(0).parseMethodDeclaration();

            renameCodeBlockName.put(className + ":" + newMethod.get("MN"), className + ":" + oldMethod.get("MN"));
        }
    },
    Reorder_Parameter {

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            HashMap<String, String> oldMethod = left.get(left.size() - 1).parseMethodDeclaration();//parse the method name
            HashMap<String, String> newMethod = right.get(right.size() - 1).parseMethodDeclaration();
            String oldSig = className + ":" + oldMethod.get("MN");
            String newSig = className + ":" + newMethod.get("MN");

            renameCodeBlockName.put(newSig ,oldSig);
        }
    },
    Parameterize_Attribute {

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert right.size() == 2;
            assert left.size() == 2;
            String oldAttri = left.get(0).parseAttributeOrParameter();
            String newAttri = right.get(0).parseAttributeOrParameter();
            HashMap<String, String> oldMethod = left.get(1).parseMethodDeclaration();
            HashMap<String, String> newMethod = right.get(1).parseMethodDeclaration();

            Jedis jedis = JedisUtil.getJedis();
            assert jedis.exists(className + ":" + oldAttri);

            renameCodeBlockName.put(className + ":" + newAttri, className + ":" + oldAttri);
            renameCodeBlockName.put(className + ":" + newMethod.get("MN"), className + ":" + oldMethod.get("MN"));
        }
    },
    Pull_Up_Attribute {//把子类中的属性 移到父类中 跨文件

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClass = defaultPackage(r.getFirstClassName());
            String newClass = defaultPackage(r.getLastClassName());
            assert r.getRightSideLocations().size() == 1;
            String oldAttri = r.getLeftSideLocations().get(0).parseAttributeOrParameter();//parse the attribute name
            String newAttri = r.getRightSideLocations().get(0).parseAttributeOrParameter();
            renameCodeBlockName.put(newClass + ":" + newAttri, oldClass + ":" + oldAttri);

        }
    },
    Push_Down_Attribute {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClass = defaultPackage(r.getFirstClassName());
            String newClass = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;
            String oldAttri = left.get(0).parseAttributeOrParameter();//parse the attribute name
            String newAttri = right.get(0).parseAttributeOrParameter();
            String oldSig = oldClass + ":" + oldAttri;
            String newSig = newClass + ":" + newAttri;

            renameCodeBlockName.put(newSig, oldSig);

        }
    },
    Move_Attribute {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClass = defaultPackage(r.getFirstClassName());
            String newClass = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;
            String oldAttri = left.get(0).parseAttributeOrParameter();//parse the method name
            String newAttri = right.get(0).parseAttributeOrParameter();
            String oldSig = oldClass + ":" + oldAttri;
            String newSig = newClass + ":" + newAttri;

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Rename_Attribute {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;
            String oldName = left.get(0).parseAttributeOrParameter();
            String newName = right.get(0).parseAttributeOrParameter();

            renameCodeBlockName.put(className + ":" + newName, className + ":" + oldName);
        }
    },
    Merge_Attribute {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Split_Attribute {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Change_Attribute_Type {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String className = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert left.size() == right.size();
            assert left.size() == 1;
            String oldName = left.get(0).parseAttributeOrParameter();
            String newName = right.get(0).parseAttributeOrParameter();

            renameCodeBlockName.put(className + ":" + newName, className + ":" + oldName);// update signature
        }
    },
    Extract_Attribute {//涉及增加新的attribute

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Encapsulate_Attribute {

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Inline_Attribute {//remove_attribute, 去掉属性，直接使用属性的值,从旧的类中移除

        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Move_And_Rename_Attribute {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {
            String oldClass = defaultPackage(r.getFirstClassName());
            String newClass = defaultPackage(r.getLastClassName());
            List<SideLocation> left = r.getLeftSideLocations();
            List<SideLocation> right = r.getRightSideLocations();
            assert oldClass.contains(".");
            assert newClass.contains(".");
            assert left.size() == right.size();
            assert left.size() == 1;
            String oldAttri = left.get(0).parseAttributeOrParameter();//parse the method name
            String newAttri = right.get(0).parseAttributeOrParameter();
            String oldSig = oldClass + ":" + oldAttri;
            String newSig = newClass + ":" + newAttri;

            renameCodeBlockName.put(newSig, oldSig);
        }
    },
    Replace_Attribute_With_Variable {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    Replace_Anonymous_With_Lambda {
        @Override
        public void apply(Map<String, String> renameCodeBlockName, Refactoring r) {

        }
    },
    ;

    public abstract void apply(Map<String, String> renameCodeBlockName, Refactoring r);

}

