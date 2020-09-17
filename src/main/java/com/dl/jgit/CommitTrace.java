package com.dl.jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;

public class CommitTrace {

    final static String GIT_SOURCE = System.getProperty("user.dir") + "/.git";

    public static void main(String[] args) throws Exception {
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(GIT_SOURCE)).build();

        // Check head with previous one commit
        RevCommit headCommit = getHeadCommit(repository);
        RevCommit diffWith = headCommit.getParent(0);
        FileOutputStream stdout = new FileOutputStream(FileDescriptor.out);
        try (DiffFormatter diffFormatter = new DiffFormatter(stdout)) {
            diffFormatter.setRepository(repository);
            for (DiffEntry entry : diffFormatter.scan(diffWith, headCommit)) {
                diffFormatter.format(diffFormatter.toFileHeader(entry));
            }
        }
    }

    private static RevCommit getHeadCommit(Repository repository) throws Exception {
        try (Git git = new Git(repository)) {
            Iterable<RevCommit> history = git.log().setMaxCount(1).call();
            return history.iterator().next();
        }
    }
}
