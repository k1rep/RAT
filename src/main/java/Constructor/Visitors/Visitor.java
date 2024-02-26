package Constructor.Visitors;

import Constructor.Enums.CodeBlockType;
import Constructor.Enums.OpeTypeEnum;
import Constructor.Enums.Operator;
import Model.*;
import Project.Utils.DiffFile;
import Util.JedisUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uom.java.xmi.*;
import gr.uom.java.xmi.decomposition.VariableDeclaration;
import org.eclipse.jdt.core.dom.*;
import redis.clients.jedis.Jedis;

import java.util.*;

public class Visitor {
    private List<CodeBlock> codeBlocks;
    private CommitCodeChange commitCodeChange;
    private HashMap<String, CodeBlock> residualMethodMap;
    private Map<String, DiffFile> diffMap;
    private Map<String, String> renameCodeBlockName;

    public void visit(Map<String, String> javaFileContents, List<CodeBlock> codeBlocks, List<CommitCodeChange> codeChange, Set<String> repositoryDirectories, Map<String, DiffFile> fileList, Map<String, String> renameCodeBlockName) {
        this.codeBlocks = codeBlocks;
        this.commitCodeChange = codeChange.get(codeChange.size() - 1); //获得当前commit的内容
        this.residualMethodMap = new HashMap<>();
        this.diffMap = fileList;
        this.renameCodeBlockName = renameCodeBlockName;

        Reader reader= new Reader(javaFileContents, repositoryDirectories);
    }

    private class Reader extends ASTReader {
        public Reader(Map<String, String> javaFileContents, Set<String> repositoryDirectories) {
            super(javaFileContents, repositoryDirectories);
        }

        protected void processCompilationUnit(String sourceFilePath, CompilationUnit compilationUnit, String javaFileContent) {
            Jedis jedis = JedisUtil.getJedis();
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                PackageDeclaration packageDeclaration = compilationUnit.getPackage();
                String packageName;
                if (packageDeclaration != null) {
                    packageName = packageDeclaration.getName().getFullyQualifiedName();
                } else {
                    packageName = "";
                }
                CodeBlock codeBlock;
                PackageTime packageTime;
                if (!jedis.exists(packageName)) {
                    if (renameCodeBlockName.containsKey(packageName) && jedis.exists(renameCodeBlockName.get(packageName))) {
                        codeBlock = objectMapper.readValue(jedis.get(renameCodeBlockName.get(packageName)), CodeBlock.class);
                        packageTime = (PackageTime) codeBlock.getLastHistory().clone();
                        commitCodeChange.addCodeChange(packageTime);
                        codeBlock.addHistory(packageTime);
                        jedis.set(packageName, objectMapper.writeValueAsString(codeBlock));
                    } else {
                        codeBlock = new CodeBlock(codeBlocks.size() + 1, CodeBlockType.Package);
                        jedis.set(packageName, objectMapper.writeValueAsString(codeBlock));
                        codeBlocks.add(codeBlock);
                        packageTime = new PackageTime(packageName, commitCodeChange, Operator.Add_Package, codeBlock);
                    }
                } else {
                    codeBlock = objectMapper.readValue(jedis.get(packageName), CodeBlock.class);
                    packageTime = (PackageTime) codeBlock.getLastHistory().clone();
                    commitCodeChange.addCodeChange(packageTime);
                    codeBlock.addHistory(packageTime);
                }
            }catch (JsonProcessingException e){
                e.printStackTrace();
            }
            super.processCompilationUnit(sourceFilePath, compilationUnit, javaFileContent);
        }

        @Override
        protected void processEnumDeclaration(CompilationUnit cu, EnumDeclaration enumDeclaration, String packageName, String sourceFile, List<UMLImport> importedTypes, UMLJavadoc packageDoc, List<UMLComment> comments){
            Jedis jedis = JedisUtil.getJedis();
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                String className = enumDeclaration.getName().getFullyQualifiedName();
                String signature = packageName.equals("") ? className : packageName + "." + className;


                CodeBlock pkgBlock = objectMapper.readValue(jedis.get(packageName), CodeBlock.class);
                String anotherName = packageName;
                if (anotherName.startsWith(".")) {
                    anotherName = anotherName.substring(1);
                }
                if (pkgBlock == null && residualMethodMap.containsKey(anotherName)) {
                    jedis.set(packageName, objectMapper.writeValueAsString(residualMethodMap.get(anotherName)));
                    pkgBlock = objectMapper.readValue(jedis.get(packageName), CodeBlock.class);
                }

                CodeBlock codeBlock;
                ClassTime classTime = null;
                CodeBlockTime oldTime = null;
                int startLine = cu.getLineNumber(enumDeclaration.getStartPosition());
                int endLine = cu.getLineNumber(enumDeclaration.getStartPosition() + enumDeclaration.getLength() - 1);

                if (!jedis.exists(signature)) {
                    if (renameCodeBlockName.containsKey(signature) && jedis.exists(renameCodeBlockName.get(signature))) {
                        codeBlock = objectMapper.readValue(jedis.get(renameCodeBlockName.get(signature)), CodeBlock.class);
                        oldTime = codeBlock.getLastHistory();
                        classTime = (ClassTime) codeBlock.getLastHistory().clone();
                        commitCodeChange.addCodeChange(classTime);
                        codeBlock.addHistory(classTime);
                        jedis.set(signature, objectMapper.writeValueAsString(codeBlock));
                    } else {
                        codeBlock = new CodeBlock(codeBlocks.size() + 1, CodeBlockType.Class);
                        jedis.set(signature, objectMapper.writeValueAsString(codeBlock));
                        codeBlocks.add(codeBlock);
                        oldTime = codeBlock.getLastHistory();
                        classTime = new ClassTime(className, commitCodeChange, Operator.Add_Class, codeBlock, pkgBlock);
                    }
                } else {
                    for (int i = startLine; i <= endLine; i++) {
                        if (diffMap.containsKey(sourceFile) && diffMap.get(sourceFile).containsChangeLine(i)) {
                            codeBlock = objectMapper.readValue(jedis.get(signature), CodeBlock.class);
                            oldTime = codeBlock.getLastHistory();
                            classTime = (ClassTime) codeBlock.getLastHistory().clone();
                            commitCodeChange.addCodeChange(classTime);
                            codeBlock.addHistory(classTime);
                            break;
                        }
                    }

                }

                if (classTime != null) {
                    classTime.setNewStartLineNum(startLine);
                    classTime.setNewEndLineNum(endLine);

                    if (oldTime != null) {
                        classTime.setOldStartLineNum(oldTime.getNewStartLineNum());
                        classTime.setOldEndLineNum(oldTime.getNewEndLineNum());
                    }
                }

            }catch (JsonProcessingException e){
                e.printStackTrace();
            }
            super.processEnumDeclaration(cu, enumDeclaration, packageName, sourceFile, importedTypes, packageDoc, comments);

        }

        @Override
        protected void processTypeDeclaration(CompilationUnit cu, TypeDeclaration typeDeclaration, String packageName, String sourceFile, List<UMLImport> importedTypes, UMLJavadoc packageDoc, List<UMLComment> comments){
            Jedis jedis = JedisUtil.getJedis();
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                String className = typeDeclaration.getName().getFullyQualifiedName();
                String signature = packageName.equals("") ? className : packageName + "." + className;

                CodeBlock pkgBlock = objectMapper.readValue(jedis.get(packageName), CodeBlock.class);
                String anotherName = packageName;
                if (anotherName.startsWith(".")) {
                    anotherName = anotherName.substring(1);
                }
                if (pkgBlock == null && residualMethodMap.containsKey(anotherName)) {
                    jedis.set(packageName, objectMapper.writeValueAsString(residualMethodMap.get(anotherName)));
                    pkgBlock = objectMapper.readValue(jedis.get(packageName), CodeBlock.class);
                }

                CodeBlock codeBlock;
                ClassTime classTime = null;
                CodeBlockTime oldTime = null;
                int startLine = cu.getLineNumber(typeDeclaration.getStartPosition());
                int endLine = cu.getLineNumber(typeDeclaration.getStartPosition() + typeDeclaration.getLength() - 1);

                if (!jedis.exists(signature)) {
                    if (renameCodeBlockName.containsKey(signature) && jedis.exists(renameCodeBlockName.get(signature))) {
                        codeBlock = objectMapper.readValue(jedis.get(renameCodeBlockName.get(signature)), CodeBlock.class);
                        oldTime = codeBlock.getLastHistory();
                        classTime = (ClassTime) codeBlock.getLastHistory().clone();
                        commitCodeChange.addCodeChange(classTime);
                        codeBlock.addHistory(classTime);
                        jedis.set(signature, objectMapper.writeValueAsString(codeBlock));
                    } else {
                        codeBlock = new CodeBlock(codeBlocks.size() + 1, CodeBlockType.Class);
                        jedis.set(signature, objectMapper.writeValueAsString(codeBlock));
                        codeBlocks.add(codeBlock);
                        oldTime = codeBlock.getLastHistory();
                        classTime = new ClassTime(className, commitCodeChange, Operator.Add_Class, codeBlock, pkgBlock);
                    }
                } else {
                    for (int i = startLine; i <= endLine; i++) {
                        if (diffMap.containsKey(sourceFile) && diffMap.get(sourceFile).containsChangeLine(i)) {
                            codeBlock = objectMapper.readValue(jedis.get(signature), CodeBlock.class);
                            oldTime = codeBlock.getLastHistory();
                            classTime = (ClassTime) codeBlock.getLastHistory().clone();
                            commitCodeChange.addCodeChange(classTime);
                            codeBlock.addHistory(classTime);
                            break;
                        }
                    }

                }

                if (classTime != null) {
                    classTime.setNewStartLineNum(startLine);
                    classTime.setNewEndLineNum(endLine);

                    if (oldTime != null) {
                        classTime.setOldStartLineNum(oldTime.getNewStartLineNum());
                        classTime.setOldEndLineNum(oldTime.getNewEndLineNum());
                    }
                }

            }catch (JsonProcessingException e){
                e.printStackTrace();
            }
            super.processTypeDeclaration(cu, typeDeclaration, packageName, sourceFile, importedTypes, packageDoc, comments);

        }

        @Override
        protected Map<BodyDeclaration, VariableDeclarationContainer> processBodyDeclarations(CompilationUnit cu, AbstractTypeDeclaration abstractTypeDeclaration, String packageName, String sourceFile, List<UMLImport> importedTypes, UMLClass umlClass, UMLJavadoc packageDoc, List<UMLComment> comments) {
            Map<BodyDeclaration, VariableDeclarationContainer> map = new LinkedHashMap();
            List<BodyDeclaration> bodyDeclarations = abstractTypeDeclaration.bodyDeclarations();
            Iterator var11 = bodyDeclarations.iterator();

            while(true) {
                while(var11.hasNext()) {
                    BodyDeclaration bodyDeclaration = (BodyDeclaration)var11.next();
                    if (bodyDeclaration instanceof FieldDeclaration) {
                        FieldDeclaration fieldDeclaration = (FieldDeclaration)bodyDeclaration;
                        List<UMLAttribute> attributes = processFieldDeclaration(cu, fieldDeclaration, umlClass.isInterface(), sourceFile, comments);
                        Iterator var15 = attributes.iterator();

                        int index = 0;
                        while(var15.hasNext()) {
                            UMLAttribute attribute = (UMLAttribute)var15.next();
                            attribute.setClassName(umlClass.getName());
                            umlClass.addAttribute(attribute);
                            attributeVisitor(cu, fieldDeclaration, attribute, index, sourceFile);
                            index++;
                        }
                    } else if (bodyDeclaration instanceof MethodDeclaration) {
                        MethodDeclaration methodDeclaration = (MethodDeclaration)bodyDeclaration;
                        UMLOperation operation = processMethodDeclaration(cu, methodDeclaration, packageName, umlClass.isInterface(), sourceFile, comments);
                        operation.setClassName(umlClass.getName());
                        umlClass.addOperation(operation);
                        map.put(methodDeclaration, operation);
                        methodVisitor(cu, methodDeclaration, operation, sourceFile);
                    } else if (bodyDeclaration instanceof Initializer) {
                        Initializer initializer = (Initializer)bodyDeclaration;
                        UMLInitializer umlInitializer = processInitializer(cu, initializer, packageName, false, sourceFile, comments);
                        umlInitializer.setClassName(umlClass.getName());
                        umlClass.addInitializer(umlInitializer);
                        map.put(initializer, umlInitializer);
                    } else if (bodyDeclaration instanceof TypeDeclaration) {
                        TypeDeclaration typeDeclaration = (TypeDeclaration)bodyDeclaration;
                        processTypeDeclaration(cu, typeDeclaration, umlClass.getName(), sourceFile, importedTypes, packageDoc, comments);
                    } else if (bodyDeclaration instanceof EnumDeclaration) {
                        EnumDeclaration enumDeclaration = (EnumDeclaration)bodyDeclaration;
                        processEnumDeclaration(cu, enumDeclaration, umlClass.getName(), sourceFile, importedTypes, packageDoc, comments);
                    }
                }

                return map;
            }
        }

        @Override
        protected void processEnumConstantDeclaration(CompilationUnit cu, EnumConstantDeclaration enumConstantDeclaration, String sourceFile, UMLClass umlClass, List<UMLComment> comments) {
            Jedis jedis = JedisUtil.getJedis();
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                UMLJavadoc javadoc = generateJavadoc(cu, (BodyDeclaration) enumConstantDeclaration, (String) sourceFile);
                LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, enumConstantDeclaration, LocationInfo.CodeElementType.ENUM_CONSTANT_DECLARATION);
                UMLEnumConstant enumConstant = new UMLEnumConstant(enumConstantDeclaration.getName().getIdentifier(), UMLType.extractTypeObject(umlClass.getName()), locationInfo);
                gr.uom.java.xmi.decomposition.VariableDeclaration variableDeclaration = new VariableDeclaration(cu, sourceFile, enumConstantDeclaration);
                enumConstant.setVariableDeclaration(variableDeclaration);
                enumConstant.setJavadoc(javadoc);
                distributeComments(comments, locationInfo, enumConstant.getComments());
                enumConstant.setFinal(true);
                enumConstant.setStatic(true);
                enumConstant.setVisibility(Visibility.PUBLIC);
                List<Expression> arguments = enumConstantDeclaration.arguments();
                Iterator var11 = arguments.iterator();

                while (var11.hasNext()) {
                    Expression argument = (Expression) var11.next();
                    enumConstant.addArgument(gr.uom.java.xmi.decomposition.Visitor.stringify(argument));
                }

                enumConstant.setClassName(umlClass.getName());
                umlClass.addEnumConstant(enumConstant);

                String attributeName = umlClass.getNonQualifiedName() + "_" + enumConstant.getName();
                String signature = umlClass.getName();
                String signature_attribute = signature + ":" + attributeName;

                CodeBlock codeBlock;
                AttributeTime attriTime = null;
                CodeBlock classBlock = objectMapper.readValue(jedis.get(signature), CodeBlock.class);
                CodeBlockTime oldTime = null;
                int startLine = cu.getLineNumber(enumConstantDeclaration.getStartPosition());
                int endLine = cu.getLineNumber(enumConstantDeclaration.getStartPosition() + enumConstantDeclaration.getLength() - 1);

                if (!jedis.exists(signature_attribute)) {
                    if (renameCodeBlockName.containsKey(signature_attribute) && jedis.exists(renameCodeBlockName.get(signature_attribute))) {
                        codeBlock = objectMapper.readValue(jedis.get(renameCodeBlockName.get(signature_attribute)), CodeBlock.class);
                        oldTime = codeBlock.getLastHistory();
                        attriTime = (AttributeTime) codeBlock.getLastHistory().clone();
                        commitCodeChange.addCodeChange(attriTime);
                        codeBlock.addHistory(attriTime);
                        jedis.set(signature_attribute, objectMapper.writeValueAsString(codeBlock));
                    } else {
                        codeBlock = new CodeBlock(codeBlocks.size() + 1, CodeBlockType.Attribute);
                        jedis.set(signature_attribute, objectMapper.writeValueAsString(codeBlock));
                        codeBlocks.add(codeBlock);
                        oldTime = codeBlock.getLastHistory();
                        attriTime = new AttributeTime(attributeName, commitCodeChange, Operator.Add_Attribute, codeBlock, classBlock);
                    }
                } else {
                    for (int i = startLine; i <= endLine; i++) {
                        if (diffMap.containsKey(sourceFile) && diffMap.get(sourceFile).containsChangeLine(i)) {
                            codeBlock = objectMapper.readValue(jedis.get(signature_attribute), CodeBlock.class);
                            oldTime = codeBlock.getLastHistory();
                            attriTime = (AttributeTime) codeBlock.getLastHistory().clone();
                            commitCodeChange.addCodeChange(attriTime);
                            codeBlock.addHistory(attriTime);
                            break;
                        }
                    }
                }


                if (attriTime != null) {
                    attriTime.setNewStartLineNum(startLine);
                    attriTime.setNewEndLineNum(endLine);

                    if (oldTime != null) {
                        attriTime.setOldStartLineNum(oldTime.getNewStartLineNum());
                        attriTime.setOldEndLineNum(oldTime.getNewEndLineNum());
                    }
                }
            }catch (JsonProcessingException e){
                e.printStackTrace();
            }
        }
    }

    private void methodVisitor(CompilationUnit cu, MethodDeclaration md, UMLOperation umlOperation, String sourceFile) {
        Jedis jedis = JedisUtil.getJedis();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String signature = umlOperation.getClassName();

            //根据operation生成方法的名称与方法参数类型列表
            StringBuilder sb = new StringBuilder();
            StringBuilder parameterTypes = new StringBuilder();

            UMLParameter returnParameter = umlOperation.getReturnParameter();
            if (returnParameter != null) {
                sb.append(returnParameter).append("_");
            }

            sb.append(umlOperation.getName());

            List<UMLParameter> parameters = new ArrayList(umlOperation.getParameters());
            parameters.remove(returnParameter);
            sb.append("(");

            for (int i = 0; i < parameters.size(); ++i) {
                UMLParameter parameter = parameters.get(i);
                if (parameter.getKind().equals("in")) {
                    String parameterStr = parameter.toString();
                    parameterTypes.append(parameterStr.substring(parameterStr.indexOf(" ") + 1));
                    if (i < parameters.size() - 1) {
                        parameterTypes.append(", ");
                    }
                }
            }

            sb.append(parameterTypes);
            sb.append(")");

            String methodName = sb.toString();
            String signature_method = signature + ":" + methodName;

            //处理完毕，生成CodeBlock和CodeBlockTime
            CodeBlock codeBlock = null;
            MethodTime methodTime = null;
            CodeBlock classBlock = objectMapper.readValue(jedis.get(signature), CodeBlock.class);
            CodeBlockTime oldTime = null;
            int startLine = cu.getLineNumber(md.getStartPosition());
            int endLine = cu.getLineNumber(md.getStartPosition() + md.getLength() - 1);

            if (!jedis.exists(signature_method)) {
                if (renameCodeBlockName.containsKey(signature_method) && jedis.exists(renameCodeBlockName.get(signature_method))) {
                    codeBlock = objectMapper.readValue(jedis.get(renameCodeBlockName.get(signature_method)), CodeBlock.class);
                    oldTime = codeBlock.getLastHistory();
                    methodTime = (MethodTime) codeBlock.getLastHistory().clone();
                    commitCodeChange.addCodeChange(methodTime);
                    codeBlock.addHistory(methodTime);
                    jedis.set(signature_method, objectMapper.writeValueAsString(codeBlock));
                } else {
                    codeBlock = new CodeBlock(codeBlocks.size() + 1, CodeBlockType.Method);
                    jedis.set(signature_method, objectMapper.writeValueAsString(codeBlock));
                    codeBlocks.add(codeBlock);
                    oldTime = codeBlock.getLastHistory();
                    methodTime = new MethodTime(methodName, commitCodeChange, Operator.Add_Method, codeBlock, classBlock, parameterTypes.toString());
                }
            } else {
                for (int i = startLine; i <= endLine; i++) {
                    if (diffMap.containsKey(sourceFile) && diffMap.get(sourceFile).containsChangeLine(i)) {
                        codeBlock = objectMapper.readValue(jedis.get(signature_method), CodeBlock.class);
                        oldTime = codeBlock.getLastHistory();
                        methodTime = (MethodTime) codeBlock.getLastHistory().clone();
                        commitCodeChange.addCodeChange(methodTime);
                        codeBlock.addHistory(methodTime);
                        break;
                    }
                }
            }
            if (!residualMethodMap.containsKey(signature + "." + umlOperation.getName())) {
                residualMethodMap.put(signature + "." + umlOperation.getName(), codeBlock);
            }

            if (methodTime != null) {
                methodTime.setNewStartLineNum(startLine);
                methodTime.setNewEndLineNum(endLine);

                if (oldTime != null) {
                    methodTime.setOldStartLineNum(oldTime.getNewStartLineNum());
                    methodTime.setOldEndLineNum(oldTime.getNewEndLineNum());
                }
            }
        }catch (JsonProcessingException e) {
            e.printStackTrace();
        }


    }

    private void attributeVisitor(CompilationUnit cu, FieldDeclaration fd, UMLAttribute umlAttribute, int index, String sourceFile){
        Jedis jedis = JedisUtil.getJedis();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String attributeName = umlAttribute.getType() + "_" + umlAttribute.getName();
            String signature = umlAttribute.getClassName();
            String signature_attribute = signature + ":" + attributeName;

            CodeBlock codeBlock;
            AttributeTime attriTime = null;
            CodeBlock classBlock = objectMapper.readValue(jedis.get(signature), CodeBlock.class);
            CodeBlockTime oldTime = null;
            int startLine = cu.getLineNumber(fd.getStartPosition());
            int endLine = cu.getLineNumber(fd.getStartPosition() + fd.getLength() - 1);

            if (!jedis.exists(signature_attribute)) {
                if (renameCodeBlockName.containsKey(signature_attribute) && jedis.exists(renameCodeBlockName.get(signature_attribute))) {
                    codeBlock = objectMapper.readValue(jedis.get(renameCodeBlockName.get(signature_attribute)), CodeBlock.class);
                    oldTime = codeBlock.getLastHistory();
                    attriTime = (AttributeTime) codeBlock.getLastHistory().clone();
                    commitCodeChange.addCodeChange(attriTime);
                    codeBlock.addHistory(attriTime);
                    jedis.set(signature_attribute, objectMapper.writeValueAsString(codeBlock));
                } else {
                    codeBlock = new CodeBlock(codeBlocks.size() + 1, CodeBlockType.Attribute);
                    jedis.set(signature_attribute, objectMapper.writeValueAsString(codeBlock));
                    codeBlocks.add(codeBlock);
                    oldTime = codeBlock.getLastHistory();
                    attriTime = new AttributeTime(attributeName, commitCodeChange, Operator.Add_Attribute, codeBlock, classBlock);
                }
            } else {
                for (int i = startLine; i <= endLine; i++) {
                    if (diffMap.containsKey(sourceFile) && diffMap.get(sourceFile).containsChangeLine(i)) {
                        codeBlock = objectMapper.readValue(jedis.get(signature_attribute), CodeBlock.class);
                        oldTime = codeBlock.getLastHistory();
                        attriTime = (AttributeTime) codeBlock.getLastHistory().clone();
                        commitCodeChange.addCodeChange(attriTime);
                        codeBlock.addHistory(attriTime);
                        break;
                    }
                }
            }


            if (attriTime != null) {
                attriTime.setNewStartLineNum(startLine);
                attriTime.setNewEndLineNum(endLine);

                if (oldTime != null) {
                    attriTime.setOldStartLineNum(oldTime.getNewStartLineNum());
                    attriTime.setOldEndLineNum(oldTime.getNewEndLineNum());
                }
            }
        }catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

}
