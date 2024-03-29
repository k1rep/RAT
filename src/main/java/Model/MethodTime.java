package Model;

import Constructor.Enums.Operator;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@Data
public class MethodTime extends CodeBlockTime{

    Set<CodeBlock> callers = new HashSet<>();
    Set<CodeBlock> callees = new HashSet<>();
    String parameters;  // todo: insert into CodeBlockTime需要修改
    Set<CodeBlock> parameterType = new HashSet<>();

    public MethodTime(String name, CommitCodeChange cmt, Operator tp, CodeBlock own, CodeBlock parent, String params) {//add method
        this.name = name;
        time = cmt;
        refactorType = tp;
        owner = own;
        parentCodeBlock = parent;
        parameters = params;
        cmt.addCodeChange(this);
        own.addHistory(this);

        CodeBlockTime parentTime = parent.getLastHistory();
        if(parentTime.getMethods() != null){
            parentTime.getMethods().add(own);
        }
    }


//    public MethodTime(MethodTime methodTimeOld, CommitCodeChange commitTime, Operator tp) {//rename_method
//        name = methodTimeOld.name;
//        time = commitTime;
//        refactorType = tp;
//        owner = methodTimeOld.owner;
//        parentCodeBlock = methodTimeOld.parentCodeBlock;
//    }

    @Override
    public Object clone() {
        MethodTime methodTime;
        methodTime = (MethodTime) super.clone();
        methodTime.setCallers(new HashSet<>(callers));
        methodTime.setCallees(new HashSet<>(callees));
        methodTime.setParameterType(new HashSet<>(parameterType));
//        System.out.println("Method");
        return methodTime;
    }

    @Override
    public String getSignature() {
        return this.getParentCodeBlock().getLastHistory().getSignature()+":"+this.getName();
    }

    @Override
    public Set<CodeBlock> getPackages() {
        return null;
    }

    @Override
    public Set<CodeBlock> getClasses() {
        return null;
    }

    @Override
    public Set<CodeBlock> getMethods() {
        return null;
    }

    @Override
    public Set<CodeBlock> getAttributes() {
        return null;
    }

    @Override
    Set<CodeBlock> getParameterRetureType() {
        return parameterType;
    }

    @Override
    public String getParameters(){return parameters;}


//    @Override
//    public CodeBlockTime deepCopy() throws JsonProcessingException {
//        return null;
//    }


    //TODO 目前写成parameter List<String>形式 之后再改吧 留意java常用类型
    // byte short int long float double char boolean
}
