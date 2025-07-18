import hashlib
import sys
import shutil
import datetime
import http.server
import socketserver
import threading
import os
import subprocess
import json
import argparse
from pathlib import Path
import requests

EMPTY_CONFIG = {
    "instances_folder": str(Path.home().joinpath(".minecraft/server_instances").absolute()),
    "rp_ip": "0.0.0.0",
}

EMPTY_INSTANCE_CFG = {
    "version": "",
    "name": "",
    "memory": "2048M",
    "auto_backup": False,
    "resourcepack": "",
    "resourcepack_port": 2548
}

rp_httpd = None
rp_server_thread = None

def write_config(cfg):
    config_path = Path.home().joinpath(".minecraft/server_instances/config.json").absolute()
    config_path.parent.mkdir(parents=True, exist_ok=True)
    with open(str(config_path), 'w') as f:
        json.dump(cfg, f, indent=4)

def read_config():
    config_path = Path.home().joinpath(".minecraft/server_instances/config.json").absolute()
    if config_path.exists():
        with open(str(config_path), "r") as cfg:
            return json.load(cfg)
    else:
        write_config(EMPTY_CONFIG)
        return EMPTY_CONFIG

def get_versions():
    response = requests.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    response.raise_for_status()
    return response.json()

def get_version(ver_id):
    versions_manifest = get_versions()
    versions = versions_manifest.get('versions')

    if ver_id == "latest" or ver_id == "l":
        ver_id = versions_manifest.get('latest').get("release")
    elif ver_id == "snapshot" or ver_id == "s":
        ver_id = versions_manifest.get('latest').get("snapshot")

    version_entry = next((entry for entry in versions if entry.get('id') == ver_id), None)

    if version_entry is None:
        raise ValueError(f"Version '{ver_id}' not found.")

    response = requests.get(version_entry.get('url'))
    response.raise_for_status()
    return response.json()

def download_server(url, sha, destination):
    sha1 = hashlib.sha1()
    try:
        with requests.get(url, stream=True) as response:
            response.raise_for_status()
            with open(destination, "wb") as f:
                for chunk in response.iter_content(chunk_size=8192):
                    if chunk:
                        f.write(chunk)
                        sha1.update(chunk)
        downloaded_sha = sha1.hexdigest()
        if downloaded_sha != sha:
            raise ValueError(f"SHA1 mismatch for {destination.name}: Expected {sha}, got {downloaded_sha}")
        print(f"Download complete and SHA1 verified for {destination.name}.")
        return True
    except requests.exceptions.RequestException as e:
        raise IOError(f"Download failed for {url}: {e}")
    except Exception as e:
        raise IOError(f"Error writing file {destination}: {e}")

def check_instance(name):
    config = read_config()
    instances_path = Path(config['instances_folder'])
    instance_path = instances_path.joinpath(name)
    return instance_path.is_dir() and instance_path.joinpath("cfg.json").exists()

def create_instance(name, version, memory, auto_backup=False, resourcepack="", resourcepack_port=2548):
    config = read_config()
    instance_path = Path(config['instances_folder']).joinpath(name)
    instance_path.mkdir(parents=True, exist_ok=True)

    resolved_version = version
    if version == "latest" or version == "l":
        resolved_version = get_versions().get('latest').get("release")
    elif version == "snapshot" or version == "s":
        resolved_version = get_versions().get('latest').get("snapshot")

    instance_cfg_data = {
        "version": resolved_version,
        "name": name,
        "memory": memory,
        "auto_backup": auto_backup,
        "resourcepack": resourcepack,
        "resourcepack_port": resourcepack_port
    }
    with open(instance_path.joinpath("cfg.json"), "w") as cfg_file:
        json.dump(instance_cfg_data, cfg_file, indent=4)

    eula_path = instance_path.joinpath("eula.txt")
    if not eula_path.exists():
        with open(eula_path, "w") as f:
            f.write("eula=true\n")
    else:
        with open(eula_path, "r+") as f:
            lines = f.readlines()
            f.seek(0)
            updated = False
            for line in lines:
                if line.strip() == "eula=false":
                    f.write("eula=true\n")
                    updated = True
                else:
                    f.write(line)
            if not updated and "eula=true" not in [l.strip() for l in lines]:
                f.write("eula=true\n")
            f.truncate()


def edit_instance(name, version=None, memory=None, auto_backup=None, resourcepack=None, resourcepack_port=None):
    config = read_config()
    instance_path = Path(config['instances_folder']).joinpath(name)
    instance_cfg_path = instance_path.joinpath("cfg.json")

    if not instance_cfg_path.exists():
        raise FileNotFoundError(f"Instance configuration file not found: {instance_cfg_path}")

    with open(instance_cfg_path, "r") as f:
        conf = json.load(f)

    if version:
        if version == "latest" or version == "l":
            resolved_version = get_versions().get('latest').get("release")
        elif version == "snapshot" or version == "s":
            resolved_version = get_versions().get('latest').get("snapshot")
        else:
            resolved_version = version
        conf['version'] = resolved_version
    if memory:
        conf['memory'] = memory
    if auto_backup is not None:
        conf['auto_backup'] = auto_backup
    if resourcepack is not None:
        conf['resourcepack'] = str(Path(resourcepack).absolute()) if resourcepack else ""
    if resourcepack_port is not None:
        conf['resourcepack_port'] = resourcepack_port

    with open(instance_cfg_path, "w") as cfg_file:
        json.dump(conf, cfg_file, indent=4)

def get_instance(name):
    cfg = read_config()
    instances_path = Path(cfg['instances_folder'])
    instance_config_path = instances_path / name / "cfg.json"
    if instance_config_path.exists():
        with open(instance_config_path, "r") as f:
            instance_cfg = json.load(f)
            for key, default_value in EMPTY_INSTANCE_CFG.items():
                if key not in instance_cfg:
                    instance_cfg[key] = default_value
            return instance_cfg
    return None

def backup_instance(instance_name):
    config = read_config()
    instance_path = Path(config['instances_folder']).joinpath(instance_name)

    if not instance_path.is_dir():
        print(f"Error: Instance folder '{instance_name}' not found at '{instance_path}'.")
        return False

    backups_path = instance_path / "backups"
    backups_path.mkdir(exist_ok=True)

    timestamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    world_folder = instance_path / "world"

    source_path = world_folder
    if not world_folder.is_dir():
        print(f"Warning: 'world' folder not found for instance '{instance_name}'. Backing up the entire instance directory.")
        source_path = instance_path

    backup_name = f"{timestamp}-world-backup" if world_folder.is_dir() else f"{timestamp}-instance-backup"
    destination_path = backups_path / backup_name

    try:
        shutil.make_archive(str(destination_path), 'zip', str(source_path))
        print(f"Backup created successfully: {destination_path}.zip")
        return True
    except Exception as e:
        print(f"Error creating backup for '{instance_name}': {e}")
        return False

def delete_instance(name):
    config = read_config()
    instances_path = Path(config['instances_folder'])
    instance_path = instances_path / name

    if not check_instance(name):
        print(f"Error: Instance '{name}' does not exist.")
        return False

    try:
        shutil.rmtree(instance_path)
        print(f"Instance '{name}' deleted successfully from '{instance_path}'.")
        return True
    except OSError as e:
        print(f"Error deleting instance '{name}': {e}")
        return False

def open_instance_folder(instance_name):
    config = read_config()
    instance_path = Path(config['instances_folder']).joinpath(instance_name)

    if not instance_path.exists():
        print(f"Error: Instance folder '{instance_path}' does not exist.")
        print("Make sure the instance has been created.")
        return False

    try:
        if sys.platform == "win32":
            os.startfile(str(instance_path))
        elif sys.platform == "darwin":
            subprocess.run(['open', str(instance_path)], check=True)
        else: # Linux/Unix
            subprocess.run(['xdg-open', str(instance_path)], check=True)
        print(f"Opened folder: {instance_path}")
        return True
    except FileNotFoundError:
        print(f"Error: Could not find a suitable application to open folder '{instance_path}'.")
        return False
    except subprocess.CalledProcessError as e:
        print(f"Error opening folder: Command failed with exit code {e.returncode}. {e}")
        return False
    except Exception as e:
        print(f"An unexpected error occurred while trying to open folder: {e}")
        return False

def calculate_file_sha1(file_path):
    sha1 = hashlib.sha1()
    try:
        with open(file_path, 'rb') as f:
            while chunk := f.read(8192):
                sha1.update(chunk)
        return sha1.hexdigest()
    except FileNotFoundError:
        print(f"Error: File not found at '{file_path}' for SHA1 calculation.")
        return None
    except Exception as e:
        print(f"Error calculating SHA1 for '{file_path}': {e}")
        return None

def update_server_properties(instance_name, key, value):
    config = read_config()

    server_properties_path = Path(config['instances_folder']).joinpath(f"{instance_name}/server.properties")
    properties = {}
    lines = []

    if not server_properties_path.exists():
        print(f"Error: server.properties not found at '{server_properties_path}'.")
        return False

    with open(server_properties_path, "r") as f:
        for line in f:
            stripped = line.strip()
            if stripped and '=' in stripped and not stripped.startswith('#'):
                k, v = stripped.split('=', 1)
                properties[k.strip()] = v.strip()
            lines.append(line)

    if key not in properties:
        print(f"Key '{key}' not found in server.properties. No changes made.")
        return False

    new_lines = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith(f"{key}=") and not stripped.startswith('#'):
            new_lines.append(f"{key}={value}\n")
        else:
            new_lines.append(line)

    with open(server_properties_path, "w") as f:
        f.writelines(new_lines)

    print(f"Updated server.properties: {key}={value}")
    return True

class ResourcePackHTTPHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

def _run_http_server(directory, ip, port):
    global rp_httpd
    current_working_directory = os.getcwd()
    try:
        os.chdir(directory)
        handler = ResourcePackHTTPHandler
        rp_httpd = socketserver.TCPServer((ip, port), handler)
        print(f"Resource pack HTTP server serving files from '{directory}' at http://{ip}:{port}")
        rp_httpd.serve_forever()
    except OSError as e:
        print(f"Error starting HTTP server on {ip}:{port}: {e}. This port might already be in use or unavailable.")
    except Exception as e:
        print(f"An unexpected error occurred in resource pack HTTP server: {e}")
    finally:
        os.chdir(current_working_directory)

def start_resourcepack_http_server(file_path, ip, port):
    global rp_server_thread
    rp_path = Path(file_path)
    if not rp_path.is_file():
        print(f"Error: Resource pack file not found at '{file_path}'. Cannot start HTTP server.")
        return False

    server_directory = rp_path.parent
    rp_server_thread = threading.Thread(target=_run_http_server, args=(str(server_directory.absolute()), ip, port), daemon=True)
    rp_server_thread.start()
    return True

def stop_resourcepack_http_server():
    global rp_httpd, rp_server_thread
    if rp_httpd:
        print("Stopping resource pack HTTP server...")
        rp_httpd.shutdown()
        rp_httpd.server_close()
        rp_httpd = None
    if rp_server_thread and rp_server_thread.is_alive():
        rp_server_thread.join(timeout=1)
        if rp_server_thread.is_alive():
            print("Warning: Resource pack HTTP server thread did not terminate gracefully.")
        rp_server_thread = None

def set_resourcepack(instance_name):
    config = read_config()
    instance_cfg = get_instance(instance_name)
    rp_path = Path(instance_cfg['resourcepack'])
    if not rp_path.is_file():
        print(f"Error: Resource pack file '{rp_path.absolute()}' not found.")
        return False
    rp_sha1 = calculate_file_sha1(rp_path)
    if not rp_sha1:
        print("Failed to calculate resource pack SHA1. Aborting attachment.")
        return False

    rp_ip = config.get("rp_ip")
    if not rp_ip:
        print("Error: Resource pack IP address (rp_ip) not set in main config. Please set it using 'edit-config' command or manually edit config.json.")
        return False

    rp_url = f"http://{rp_ip}:{instance_cfg['resourcepack_port']}/{rp_path.name}"
    print(f"Constructed resource pack URL for server.properties: {rp_url}")

    update_server_properties(instance_name, "require-resource-pack", "true")
    update_server_properties(instance_name, "resource-pack", rp_url)
    update_server_properties(instance_name, "resource-pack-sha1", rp_sha1)

    print(f"Resource pack '{rp_path.name}' attached to instance '{instance_name}'.")
    print(f"Minecraft clients will attempt to download it from: {rp_url}")
    return True


def attach_resourcepack(instance_name, resourcepack_file_path, resourcepack_port=None):
    config = read_config()
    instance_cfg = get_instance(instance_name)

    if not instance_cfg:
        print(f"Error: Instance '{instance_name}' does not exist. Please create it first.")
        return False

    rp_path = Path(resourcepack_file_path)
    if not rp_path.is_file():
        print(f"Error: Resource pack file '{resourcepack_file_path}' not found.")
        return False

    edit_instance(instance_name, resourcepack=str(rp_path.absolute()),
                  resourcepack_port=resourcepack_port if resourcepack_port is not None else instance_cfg['resourcepack_port'])
    print("Resource pack file attached to '" + instance_name + "' successfully.")
    return True


def launch_server(instance_name):
    config = read_config()
    instance = get_instance(instance_name)
    if not instance:
        print(f"Instance '{instance_name}' not found.")
        return

    set_resourcepack(instance_name)
    instance_path = Path(config['instances_folder']) / instance_name
    server_jar_path = instance_path / f"server.jar"

    try:
        if instance.get('auto_backup', False):
            print(f"Auto-backup enabled. Creating backup for '{instance_name}' before launch...")
            if not backup_instance(instance_name):
                print("Auto-backup failed. Continuing with server launch anyway.")
            else:
                print("Auto-backup completed.")

        ver = get_version(instance.get('version'))
        server_jar_url = ver.get('downloads', {}).get('server', {}).get('url')
        server_sha = ver.get('downloads', {}).get('server', {}).get('sha1')

        if not server_jar_url or not server_sha:
            print("Server download information missing from version manifest.")
            return

        current_sha1 = None
        if server_jar_path.exists():
            print("Checking existing server.jar SHA1...")
            current_sha1 = calculate_file_sha1(server_jar_path)

        if not server_jar_path.exists() or current_sha1 != server_sha:
            print(f"server.jar not found or SHA1 mismatch. Downloading {instance.get('version')} server.jar...")
            download_server(server_jar_url, server_sha, server_jar_path)
        else:
            print("server.jar is up to date.")

        resourcepack_file = instance.get('resourcepack')
        resourcepack_port = instance.get('resourcepack_port')
        if resourcepack_file and Path(resourcepack_file).is_file():
            print(f"Resource pack '{Path(resourcepack_file).name}' detected. Starting HTTP server...")
            rp_ip = config.get("rp_ip", EMPTY_CONFIG["rp_ip"])
            if start_resourcepack_http_server(resourcepack_file, rp_ip, resourcepack_port):
                print(f"Resource pack HTTP server started on {rp_ip}:{resourcepack_port} serving from '{Path(resourcepack_file).parent}'.")
            else:
                print("Failed to start resource pack HTTP server. Server might not function correctly regarding resource packs.")

        java_exec = "java"

        memory_allocation = instance.get('memory', EMPTY_INSTANCE_CFG['memory'])

        command = [java_exec, f"-Xmx{memory_allocation}", f"-Xms{memory_allocation}", "-jar", str(server_jar_path), "nogui"]
        print(f"Launching server with command: {' '.join(command)}")
        server_process = subprocess.run(command, cwd=instance_path)

        stop_resourcepack_http_server()
        print(f"Server '{instance_name}' exited with code {server_process.returncode}.")

    except requests.exceptions.RequestException as e:
        print(f"Network error during server launch (e.g., getting version info, downloading JAR): {e}")
    except ValueError as e:
        print(f"Configuration or version error: {e}")
    except FileNotFoundError:
        print(f"Error: Java executable '{java_exec}' not found. Please ensure Java is installed and in your PATH.")
    except IOError as e:
        print(f"File system error during server launch: {e}")
    except Exception as e:
        print(f"An unexpected error occurred during server launch: {e}")
    finally:
        stop_resourcepack_http_server()


def list_instances():
    config = read_config()
    instances_path = Path(config['instances_folder'])
    instances_path.mkdir(parents=True, exist_ok=True)

    found_instances = []
    for instance_dir in instances_path.iterdir():
        if instance_dir.is_dir():
            cfg_file = instance_dir / "cfg.json"
            if cfg_file.exists():
                try:
                    with open(cfg_file, 'r') as f:
                        instance_cfg = json.load(f)
                        name = instance_cfg.get('name', instance_dir.name)
                        version = instance_cfg.get('version', 'N/A')
                        memory = instance_cfg.get('memory', EMPTY_INSTANCE_CFG['memory'])
                        found_instances.append({"name": name, "version": version, "memory": memory})
                except json.JSONDecodeError:
                    print(f"Warning: Could not read cfg.json for instance '{instance_dir.name}'. Skipping.")
                except Exception as e:
                    print(f"Warning: An error occurred while processing instance '{instance_dir.name}': {e}. Skipping.")

    if found_instances:
        print("\n--- Available Minecraft Server Instances ---")
        print(f"{'Name':<20} {'Version':<15} {'Memory':<10}")
        print(f"{'-'*20:<20} {'-'*15:<15} {'-'*10:<10}")
        for instance in found_instances:
            print(f"{instance['name']:<20} {instance['version']:<15} {instance['memory']:<10}")
        print("------------------------------------------")
    else:
        print("No Minecraft server instances found.")
    return found_instances

def edit_global_config(key=None, value=None):
    current_config = read_config()
    updated = False

    if key is not None and value is not None:
        if key not in current_config:
            print(f"Warning: Key '{key}' not found in current config. Skipping.")
        else:
            current_config[key] = value
            updated = True

    if updated:
        write_config(current_config)
        print("Global configuration updated successfully.")
    else:
        print("No global configuration settings provided to update.")


def main():
    parser = argparse.ArgumentParser(description="Minecraft Server Launcher by MagnarIUK")
    parser.add_argument("-i", "--instance", help="Name of The Instance")
    parser.add_argument("-ver", "--version", help="Instance Version")
    parser.add_argument("-mem", "--memory",
                        help="Memory allocation for the server (e.g., 1024M, 2G). Default is 2048M.")
    parser.add_argument("-ab","--auto-backup", action="store_true",
                        help="Enable automatic world backup before launching the server.")
    parser.add_argument("-nab","--no-auto-backup", action="store_false", dest="auto_backup",
                        help="Disable automatic world backup before launching the server.")
    parser.set_defaults(auto_backup=None)
    parser.add_argument("-rp", "--resourcepack-file",
                        help="Path to the resource pack .zip file to attach (for 'attach' and 'create' commands).")
    parser.add_argument("-rpp", "--resourcepack-port", type=int,
                        help="Port for the resource pack HTTP server (for 'attach', 'create', and 'edit' commands). Default is 2548.")
    parser.add_argument("-k", "--key",
                        help="Key for editing configs (not instances).")
    parser.add_argument("-v", "--value",
                        help="Value for editing configs (not instances).")
    parser.add_argument("-c", "--command", required=True,
                        choices=["create", "launch", "check", "edit", "backup", "delete", "open", "attach", "list", "edit-config", "edit-sp"],
                        help="Command to execute: 'create', 'launch', 'check', 'edit', 'backup', 'delete', 'open', 'attach', 'list', 'edit-config', 'edit-sp'.")


    args = parser.parse_args()

    instance_commands = ["create", "launch", "check", "edit", "backup", "delete", "open", "attach", "edit-sp"]
    if args.command in instance_commands and not args.instance:
        parser.error(f"The '{args.command}' command requires the --instance (-i) argument.")


    if args.command == "check":
        exists = check_instance(args.instance)
        if exists:
            print(f"Instance '{args.instance}' exists.")
        else:
            print(f"Instance '{args.instance}' does not exist.")

    elif args.command == "create":
        if check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' already exists.")
            return

        if not args.version:
            print("Error: Version is required to create an instance.")
            return

        memory_to_use = args.memory if args.memory else EMPTY_INSTANCE_CFG['memory']
        auto_backup_setting = args.auto_backup if args.auto_backup is not None else False
        resourcepack_path = args.resourcepack_file if args.resourcepack_file else ""
        resourcepack_port_setting = args.resourcepack_port if args.resourcepack_port is not None else EMPTY_INSTANCE_CFG['resourcepack_port']

        try:
            create_instance(args.instance, args.version, memory_to_use, auto_backup_setting,
                            resourcepack=resourcepack_path,
                            resourcepack_port=resourcepack_port_setting)
            print(f"Instance '{args.instance}' created successfully.")
            print(f"  Version: {args.version}")
            print(f"  Memory: {memory_to_use}")
            print(f"  Auto-backup: {'Enabled' if auto_backup_setting else 'Disabled'}")
            if resourcepack_path:
                print(f"  Resource pack: '{Path(resourcepack_path).name}' on port {resourcepack_port_setting}")
        except Exception as e:
            print(f"Failed to create instance '{args.instance}': {e}")


    elif args.command == "edit":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist.")
            return
        if not any([args.version, args.memory, args.auto_backup is not None, args.resourcepack_file is not None, args.resourcepack_port is not None]):
            print("Error: At least one of version, memory, auto-backup, resource pack file, or resource pack port must be provided to edit an instance.")
            return
        try:
            edit_instance(args.instance, args.version, args.memory, args.auto_backup,
                          resourcepack=args.resourcepack_file, resourcepack_port=args.resourcepack_port)
            print(f"Instance '{args.instance}' updated successfully.")
        except Exception as e:
            print(f"Failed to edit instance '{args.instance}': {e}")


    elif args.command == "launch":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist.")
            return
        launch_server(args.instance)

    elif args.command == "backup":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist.")
            return
        backup_instance(args.instance)

    elif args.command == "delete":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist.")
            return
        confirm = input(f"Are you sure you want to delete instance '{args.instance}' and all its data? (yes/no): ").lower()
        if confirm == 'yes':
            delete_instance(args.instance)
        else:
            print("Deletion cancelled.")

    elif args.command == "open":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist.")
            return
        open_instance_folder(args.instance)

    elif args.command == "attach":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist. Cannot attach resource pack.")
            return
        if not args.resourcepack_file:
            print("Error: A resource pack file path (--resourcepack-file or -rp) is required for the 'attach' command.")
            return
        attach_resourcepack(args.instance, args.resourcepack_file, args.resourcepack_port)
    elif args.command == "list":
        list_instances()

    elif args.command == "edit-config":
        if not any([args.key is not None, args.value is not None]):
            print("Error: Put in --key and --value to edit global config.")
            return
        edit_global_config(key=args.key, value=args.value)

    elif args.command == "edit-sp":
        if not any([args.key is not None, args.value is not None]):
            print("Error: Put in --key and --value to edit global config.")
            return
        exists = check_instance(args.instance)
        if exists:
            update_server_properties(args.instance, args.key, args.value)
        else:
            print(f"Error: Instance '{args.instance}' does not exist.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nExecution interrupted by user. Exiting...")
        stop_resourcepack_http_server()
        sys.exit(0)
    except Exception as e:
        print(f"An unhandled error occurred: {e}")
        stop_resourcepack_http_server()
        sys.exit(1)