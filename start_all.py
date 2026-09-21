import subprocess
import os
import atexit

processes = []

def cleanup():
    print("\nShutting down all services...")
    for p in processes:
        p.terminate()
    if 'log_file' in globals():
        log_file.close()

atexit.register(cleanup)

def start_services():
    print("========================================================")
    print("Starting Voxomos POC (Full Stack) in this terminal...")
    print("Press Ctrl+C to safely stop all services.")
    print("========================================================\n")
    
    # Get the absolute path to the directory containing this script (which is now poc-app)
    base_dir = os.path.dirname(os.path.abspath(__file__))
    frontend_dir = os.path.join(base_dir, "frontend")
    
    # Commands
    java_cmd = [r"C:\Users\harsh\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd", "clean", "compile", "exec:java", "-Dexec.mainClass=com.voxomos.poc.WebApp"]
    react_cmd = "npm run dev"
    
    try:
        print("--> Starting Java Web API (Port 8274)...")
        p2 = subprocess.Popen(java_cmd, cwd=base_dir)
        processes.append(p2)
        
        print("--> Starting React Frontend (Port 5173)...")
        # We use shell=True for React because 'npm' is a .cmd script on Windows
        p3 = subprocess.Popen(react_cmd, cwd=frontend_dir, shell=True)
        processes.append(p3)
        
        # Keep the script running, piping all output to this terminal
        # until the user presses Ctrl+C
        for p in processes:
            p.wait()
            
    except KeyboardInterrupt:
        # Handled by atexit gracefully
        pass

if __name__ == "__main__":
    start_services()
