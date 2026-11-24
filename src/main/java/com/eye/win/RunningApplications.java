package com.eye.win;

import java.util.*;
import java.nio.file.Path;

/** Read-only process snapshot. No subprocess, window titles or command line arguments. */
public final class RunningApplications {
    private RunningApplications() {}
    public static Set<String> sample(){
        Set<String> paths=new HashSet<>();
        try(var processes=ProcessHandle.allProcesses()){
            processes.forEach(process->{
                try{process.info().command().ifPresent(command->paths.add(Path.of(command).toAbsolutePath().normalize().toString()));}
                catch(SecurityException ignored){/* Inaccessible system processes cannot be measured. */}
            });
        }
        return paths;
    }
}
