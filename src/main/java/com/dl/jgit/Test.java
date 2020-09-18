package com.dl.jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Test {

    final static String GIT_SOURCE = System.getProperty("user.dir") + "/.git";

    public static void abc(String[] args) throws Exception {
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(GIT_SOURCE)).build();

        // Check head with previous one commit
        RevCommit headCommit = getHeadCommit(repository);
        RevCommit diffWith = headCommit.getParent(0);
//        FileOutputStream stdout = new FileOutputStream(FileDescriptor.out);

        Map<String, String> diffTrace = new LinkedHashMap<>();
        List changes = new LinkedList();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (DiffFormatter diffFormatter = new DiffFormatter(stdout)) {
            diffFormatter.setRepository(repository);
            List<DiffEntry> entries = diffFormatter.scan(diffWith, headCommit);
            for (DiffEntry entry : entries) {
//                diffFormatter.format(diffFormatter.toFileHeader(entry));
                String filename = entry.getNewPath();
                if (filename.endsWith(".java")) {
                    diffFormatter.format(entry);
                    diffTrace.put("file", entry.getNewPath());
                    diffTrace.put("diff", stdout.toString("UTF-8"));
                    diffTrace.put("type", entry.getChangeType().toString());
                    changes.add(diffTrace);
                }
            }
        }
        System.out.println(changes);
//        System.out.println(stdout.toString("UTF-8"));
//        stdout.reset();
    }

    private static RevCommit getHeadCommit(Repository repository) throws Exception {
        try (Git git = new Git(repository)) {
            Iterable<RevCommit> history = git.log().setMaxCount(1).call();
            return history.iterator().next();
        }
    }
}
