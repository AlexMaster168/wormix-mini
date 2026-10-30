using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Rift.Editor {
    public static class BuildWindows {
        public static void Build() {
            string output=Environment.GetEnvironmentVariable("RIFT_PC_OUTPUT");
            if(string.IsNullOrEmpty(output)) output=Path.GetFullPath("Builds/Windows/RIFT-Artillery.exe");
            Directory.CreateDirectory(Path.GetDirectoryName(output));Directory.CreateDirectory("Assets/Scenes");
            var scene=EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Single);
            EditorSceneManager.SaveScene(scene,"Assets/Scenes/Rift.unity");
            PlayerSettings.companyName="RIFT";PlayerSettings.productName="RIFT Artillery";PlayerSettings.bundleVersion="0.4.0";
            PlayerSettings.defaultScreenWidth=1600;PlayerSettings.defaultScreenHeight=900;PlayerSettings.fullScreenMode=FullScreenMode.Windowed;PlayerSettings.runInBackground=true;
            PlayerSettings.SetScriptingBackend(UnityEditor.Build.NamedBuildTarget.Standalone,ScriptingImplementation.Mono2x);
            var report=BuildPipeline.BuildPlayer(new BuildPlayerOptions {scenes=new[]{"Assets/Scenes/Rift.unity"},locationPathName=output,target=BuildTarget.StandaloneWindows64,options=BuildOptions.None});
            if(report.summary.result!=BuildResult.Succeeded) throw new Exception("Windows build failed: "+report.summary.result);
            File.WriteAllText(Path.Combine(Path.GetDirectoryName(output),"build-result.txt"),"PASS: Unity Windows64 0.4.0\nBytes: "+report.summary.totalSize+"\n");
        }
    }
}
