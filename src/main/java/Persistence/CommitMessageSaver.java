package Persistence;

import Model.CodeBlock;
import Util.sqlite.SqliteHelper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CommitMessageSaver {
    private static final Logger logger = LoggerFactory.getLogger(MappingSaver.class);
    private SqliteHelper helper;

    public CommitMessageSaver(String dbFilePath) {
        try {
            helper = new SqliteHelper(dbFilePath);
        } catch (SQLException | ClassNotFoundException e) {
            logger.error(e.getMessage());
        }
    }
    public void close(){
        helper.destroyed();
    }

    public void save(String startHash, String endHash, Repository repository) {
        try {
            PreparedStatement preparedStatement = helper.getPreparedStatement("insert into CommitMessage values(?,?,?);");
            // 获取两个commit之间所有commit的哈希值
            List<String> commitHashes = getCommitHashesBetweenCommits(repository, startHash, endHash);

            // 遍历每个commit的哈希值，获取commit的信息
            for (String commitHash : commitHashes) {
                RevCommit commit = repository.parseCommit(ObjectId.fromString(commitHash));
                String message = commit.getFullMessage();
                String author = commit.getAuthorIdent().getName();
                String time = commit.getAuthorIdent().getWhen().toString();

                preparedStatement.setString(1,message);
                preparedStatement.setString(2,author);
                preparedStatement.setString(3,time);
                preparedStatement.addBatch();
            }

            helper.executePreparedStatement(preparedStatement);
            helper.destroyed();  // 手动关闭连接
        } catch (SQLException | ClassNotFoundException e) {
            logger.error(e.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<String> getCommitHashesBetweenCommits(Repository repository, String commitHash1, String commitHash2) throws Exception {
        List<String> commitHashes = new ArrayList<>();

        // 解析commit1和commit2的ObjectId
        ObjectId objectId1 = ObjectId.fromString(commitHash1);
        ObjectId objectId2 = ObjectId.fromString(commitHash2);

        // 使用RevWalk遍历commit
        try (RevWalk revWalk = new RevWalk(repository)) {
            revWalk.markStart(revWalk.parseCommit(objectId1));
            revWalk.markUninteresting(revWalk.parseCommit(objectId2));

            for (RevCommit commit : revWalk) {
                commitHashes.add(commit.getName());
            }
        }

        return commitHashes;
    }
}
