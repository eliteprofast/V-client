using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Windows.Forms;

internal static class StandaloneInstaller
{
    private const string PayloadResource = "VSMPClient.Payload.zip";
    private const string ExecutableName = "V-Client.exe";

    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length == 1 && args[0] == "--verify")
        {
            return VerifyPayload();
        }

        try
        {
            string installDirectory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Programs",
                "VSMP Client");
            string executablePath = Path.Combine(installDirectory, ExecutableName);

            Process[] runningLaunchers = Process.GetProcessesByName("V-Client");
            if (runningLaunchers.Length != 0)
            {
                throw new InvalidOperationException(
                    "Close V-Client before installing or updating it, then run this setup file again.");
            }

            ExtractPayload(installDirectory);
            Process.Start(new ProcessStartInfo
            {
                FileName = executablePath,
                WorkingDirectory = installDirectory,
                UseShellExecute = true
            });
            return 0;
        }
        catch (Exception exception)
        {
            MessageBox.Show(
                "V-Client could not be installed or started.\r\n\r\n" + exception.Message,
                "VSMP Client Setup",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
            return 1;
        }
    }

    private static int VerifyPayload()
    {
        try
        {
            using (Stream payload = OpenPayload())
            using (ZipArchive archive = new ZipArchive(payload, ZipArchiveMode.Read))
            {
                string[] requiredEntries =
                {
                    "Release/V-Client.exe",
                    "Release/app/V-Client.cfg",
                    "Release/runtime/lib/modules",
                    "Release/runtimes/zulu21/bin/java.exe"
                };
                string[] entries = archive.Entries.Select(entry => entry.FullName).ToArray();
                foreach (string requiredEntry in requiredEntries)
                {
                    if (!entries.Contains(requiredEntry, StringComparer.Ordinal))
                    {
                        throw new InvalidDataException(
                            "The embedded application bundle is missing " + requiredEntry + ".");
                    }
                }

                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    if (!entry.FullName.StartsWith("Release/", StringComparison.Ordinal))
                    {
                        throw new InvalidDataException(
                            "The embedded application bundle contains an unexpected path.");
                    }

                    using (Stream file = entry.Open())
                    {
                        file.CopyTo(Stream.Null);
                    }
                }
            }

            string verificationDirectory = Path.Combine(
                Path.GetTempPath(), "VSMP-Setup-Verify-" + Guid.NewGuid().ToString("N"));
            try
            {
                ExtractPayload(verificationDirectory);
                string runtimeModules = Path.Combine(verificationDirectory, "runtime", "lib", "modules");
                string javaRuntime = Path.Combine(
                    verificationDirectory, "runtimes", "zulu21", "bin", "java.exe");
                if (!File.Exists(runtimeModules) || !File.Exists(javaRuntime))
                {
                    throw new InvalidDataException(
                        "The extracted bundle is missing a required Java runtime file.");
                }
            }
            finally
            {
                if (Directory.Exists(verificationDirectory))
                {
                    Directory.Delete(verificationDirectory, true);
                }
            }

            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine(exception);
            return 1;
        }
    }

    private static void ExtractPayload(string installDirectory)
    {
        string installRoot = Path.GetFullPath(installDirectory) + Path.DirectorySeparatorChar;
        Directory.CreateDirectory(installDirectory);

        using (Stream payload = OpenPayload())
        using (ZipArchive archive = new ZipArchive(payload, ZipArchiveMode.Read))
        {
            foreach (ZipArchiveEntry entry in archive.Entries)
            {
                if (!entry.FullName.StartsWith("Release/", StringComparison.Ordinal)
                    || entry.FullName.StartsWith("Release/Source/", StringComparison.Ordinal))
                {
                    continue;
                }

                string relativePath = entry.FullName.Substring("Release/".Length)
                    .Replace('/', Path.DirectorySeparatorChar);
                if (relativePath.Length == 0)
                {
                    continue;
                }

                string destination = Path.GetFullPath(Path.Combine(installDirectory, relativePath));
                if (!destination.StartsWith(installRoot, StringComparison.OrdinalIgnoreCase))
                {
                    throw new InvalidDataException(
                        "The application bundle contains an unsafe file path.");
                }

                if (entry.FullName.EndsWith("/", StringComparison.Ordinal))
                {
                    Directory.CreateDirectory(destination);
                    continue;
                }

                string parent = Path.GetDirectoryName(destination);
                if (parent == null)
                {
                    throw new InvalidDataException("Could not determine an application file's directory.");
                }

                Directory.CreateDirectory(parent);
                using (Stream input = entry.Open())
                using (FileStream output = new FileStream(destination, FileMode.Create, FileAccess.Write))
                {
                    input.CopyTo(output);
                }
            }
        }

        string executablePath = Path.Combine(installDirectory, ExecutableName);
        if (!File.Exists(executablePath))
        {
            throw new FileNotFoundException(
                "The application bundle did not contain V-Client.exe.", executablePath);
        }
    }

    private static Stream OpenPayload()
    {
        Stream payload = Assembly.GetExecutingAssembly()
            .GetManifestResourceStream(PayloadResource);
        if (payload == null)
        {
            throw new InvalidDataException("The setup file does not contain the application bundle.");
        }

        return payload;
    }
}
