import hashlib
import random
import string
import sys
import shutil
from datetime import datetime, timedelta, timezone
import http.server
import socketserver
import threading
import os
import subprocess
import json
import argparse
import zipfile
from pathlib import Path
import requests

EMPTY_CONFIG = {
    "instances_folder": str(Path.home().joinpath(".minecraft/server_instances").absolute()),
    "rp_ip": "0.0.0.0",
    "api": "",
    "api_login": "",
    "api_password": "",
    "backup_on_rollback": True,
}

EMPTY_INSTANCE_CFG = {
    "version": "",
    "name": "",
    "memory": "2048M",
    "auto_backup": False,
    "host_resourcepack": False,
    "resourcepack": "",
    "resourcepack_port": 2548,
    "backups": {

    }
}
EMPTY_BACKUP_CFG = {
    "version": "",
    "datetime": "",
    "desc": ""
}

rp_httpd = None
rp_server_thread = None

def write_config(cfg):
    config_path = Path.home().joinpath(".minecraft/server_instances/config.json").absolute()
    config_path.parent.mkdir(parents=True, exist_ok=True)
    with open(config_path, 'w') as f:
        json.dump(cfg, f, indent=4)

def read_config():
    config_path = Path.home().joinpath(".minecraft/server_instances/config.json").absolute()

    if config_path.exists():
        with open(config_path, "r") as f:
            cfg = json.load(f)
    else:
        cfg = {}
    updated = False
    for key, value in EMPTY_CONFIG.items():
        if key not in cfg:
            cfg[key] = value
            updated = True
    if updated:
        write_config(cfg)

    return cfg


def upload_file(file):
    config = read_config()
    file_path = Path(file)
    if file_path.exists():
        session = requests.Session()
        print("Loging into the API...")
        api = config['api']
        api_login = config['api_login']
        api_password = config['api_password']

        login_response = session.post(api + "api/auth/login", data={
            "username": api_login,
            "password": api_password,
        })
        login_response.raise_for_status()
        print("Uploading file...")
        with open(file, 'rb') as f:
            files = {
                "file": (file_path.name, f, "application/zip")
            }
            password = generate_upload_password()
            data = {
                "expires_at": generate_expiry_date(),
                "allow_direct_link": "true",
                "create_short_link": "false",
                "downloads_limit": "0",
                "password": password
            }
            upload_response = session.post(api + "api/files/upload", files=files, data=data)
            upload_response.raise_for_status()
            res = upload_response.json()
            print(res.get("message"))
            return api + f"api/files/direct/{res['file_token']}.zip?password={password}"
    return None

def generate_expiry_date(days=30):
    dt = datetime.now(timezone.utc) + timedelta(days=30)
    return dt.isoformat(timespec='milliseconds')


def generate_upload_password(n=14):
    symbols = string.ascii_letters + string.digits
    password = ''.join(random.choice(symbols) for _ in range(n))
    return password


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
    if version in ("latest", "l"):
        resolved_version = get_versions().get('latest').get("release")
    elif version in ("snapshot", "s"):
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


def edit_instance(name, version=None, memory=None, auto_backup=None, resourcepack=None, resourcepack_port=None, backups=None):
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
        rp_is_link = True if resourcepack.startswith("http://") or resourcepack.startswith("https://") else False
        if rp_is_link:
            conf['resourcepack'] = resourcepack
        else:
            conf['resourcepack'] = str(Path(resourcepack).absolute()) if resourcepack else ""
    if resourcepack_port is not None:
        conf['resourcepack_port'] = resourcepack_port
    if backups is not None:
        conf['backups'] = backups

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

def random_hex_number(size=1):
    res = ""
    for _ in range(size):
        res += ''.join(random.choices('0123456789abcdef', k=2))
    return res

def zip_with_progress(source_path: Path, destination_path: Path):
    file_list = []
    for root, _, files in os.walk(source_path):
        for file in files:
            full_path = Path(root) / file
            rel_path = full_path.relative_to(source_path)
            file_list.append((full_path, rel_path))

    total = len(file_list)
    if total == 0:
        print("No files to back up.")
        return False

    with zipfile.ZipFile(str(destination_path) + '.zip', 'w', zipfile.ZIP_DEFLATED) as zipf:
        for i, (full_path, rel_path) in enumerate(file_list, 1):
            zipf.write(full_path, rel_path)
            if i % max(1, total // 10) == 0 or i == total:
                print(f"Backup progress: {i}/{total} files ({(i / total * 100):.0f}%)")

    return True

def backup_instance(instance_name, desc=""):
    config = read_config()
    instance_path = Path(config['instances_folder']).joinpath(instance_name)
    instance_cfg = get_instance(instance_name)
    backup = EMPTY_BACKUP_CFG.copy()
    backup['version'] = instance_cfg['version']
    timestamp = datetime.now().strftime("%Y.%m.%d-%H:%M:%S")
    backup['datetime'] = timestamp
    backup['desc'] = desc
    instance_backups = instance_cfg['backups']
    backup_id = random_hex_number()
    while backup_id in instance_backups:
        backup_id = random_hex_number()

    if not instance_path.is_dir():
        print(f"Error: Instance folder '{instance_name}' not found at '{instance_path}'.")
        return False

    backups_path = instance_path / "backups"
    backups_path.mkdir(exist_ok=True)

    world_folder = instance_path / "world"

    source_path = world_folder
    if not world_folder.is_dir():
        print(f"Error: 'world' folder not found for instance '{instance_name}'.")
        return False

    backup_name = f"{timestamp.replace('.', '').replace(":", '')}-world-backup" if world_folder.is_dir() else f"{timestamp}-instance-backup"
    destination_path = backups_path / backup_name

    try:
        #shutil.make_archive(str(destination_path), 'zip', str(source_path))
        if not zip_with_progress(source_path, destination_path):
            return False

        print(f"Backup created successfully: {destination_path}.zip")
        instance_backups[backup_id] = backup
        edit_instance(instance_name, backups=instance_backups)
        return True
    except Exception as e:
        print(f"Error creating backup for '{instance_name}': {e}")
        return False

def rollback_instance(instance_name, backup_id):
    config = read_config()
    instance_path = Path(config['instances_folder']) / instance_name
    instance_cfg = get_instance(instance_name)

    backups = instance_cfg.get("backups", {})
    if backup_id not in backups:
        print(f"Error: Backup ID '{backup_id}' not found for instance '{instance_name}'.")
        return False

    backup_info = backups[backup_id]
    timestamp = backup_info['datetime']
    version = backup_info['version']

    file_name_base = f"{timestamp.replace('.', '').replace(':', '')}-world-backup"
    backup_zip_path = instance_path / "backups" / f"{file_name_base}.zip"

    if not backup_zip_path.exists():
        print(f"Error: Backup file not found at '{backup_zip_path}'.")
        return False

    if config['backup_on_rollback']:
        print(f"Creating automatic backup before rollback...")
        if not backup_instance(instance_name, desc=f"Auto-backup before rollback to {backup_id}"):
            print("Error: Failed to create backup before rollback. Aborting.")
            return False

    print(f"Rolling back instance '{instance_name}' using backup '{backup_id}'...")

    world_folder = instance_path / "world"
    if world_folder.exists():
        try:
            print("Deleting existing world folder...")
            shutil.rmtree(world_folder)
        except Exception as e:
            print(f"Error removing old world folder: {e}")
            return False

    try:
        print(f"Extracting backup '{backup_zip_path.name}' into 'world' folder...")
        world_folder.mkdir(exist_ok=True)
        with zipfile.ZipFile(backup_zip_path, 'r') as zip_ref:
            zip_ref.extractall(world_folder)
        print("Rollback complete.")
        return True
    except Exception as e:
        print(f"Error during extraction: {e}")
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
        else:
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
def calculate_remote_file_sha1(url, chunk_size=8192):
    sha1_hash = hashlib.sha1()
    try:
        with requests.get(url, stream=True) as r:
            r.raise_for_status()
            for chunk in r.iter_content(chunk_size=chunk_size):
                if chunk:
                    sha1_hash.update(chunk)
        return sha1_hash.hexdigest()
    except requests.exceptions.RequestException as e:
        print(f"Error downloading file from {url}: {e}")
        return None
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
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
    rp_server_thread = threading.Thread(target=_run_http_server, args=(str(server_directory.absolute()), '0.0.0.0', port), daemon=True)
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
    rp_value = instance_cfg['resourcepack']

    if not rp_value:
        print("No resource pack specified for this instance. Disabling resource pack settings in server.properties.")
        update_server_properties(instance_name, "require-resource-pack", "false")
        update_server_properties(instance_name, "resource-pack", "")
        update_server_properties(instance_name, "resource-pack-sha1", "")
        return True

    is_url = rp_value.startswith("http://") or rp_value.startswith("https://")

    if is_url:
        rp_url = rp_value
        print(f"Resource pack is a URL: {rp_url}")
        print("No local hosting required.")
        calculated_sha1 = calculate_remote_file_sha1(rp_url)
        if not calculated_sha1:
            print("Failed to calculate resource pack SHA1 for remote file. Aborting attachment.")
            return False
        rp_sha1 = calculated_sha1
    else:
        rp_path = Path(rp_value)
        if not rp_path.is_file():
            print(f"Error: Resource pack file '{rp_path.absolute()}' not found. Cannot attach.")
            return False

        calculated_sha1 = calculate_file_sha1(rp_path)
        if not calculated_sha1:
            print("Failed to calculate resource pack SHA1 for local file. Aborting attachment.")
            return False
        rp_sha1 = calculated_sha1

        rp_ip = config.get("rp_ip")
        if not rp_ip:
            print(
                "Error: Resource pack IP address (rp_ip) not set in main config. Please set it using 'edit-config' command or manually edit config.json.")
            return False

        rp_url = f"http://{rp_ip}:{instance_cfg['resourcepack_port']}/{rp_path.name}"
        print(f"Constructed resource pack URL for server.properties (local file): {rp_url}")

        print(f"Resource pack '{rp_path.name}' attached to instance '{instance_name}'.")
        print(f"Minecraft clients will attempt to download it from: {rp_url}")

        print(f"Starting local HTTP server for resource pack: {rp_path.name}")
        if not start_resourcepack_http_server(rp_path, rp_ip, instance_cfg['resourcepack_port']):
            print(
                "Failed to start resource pack HTTP server. Server might not function correctly regarding resource packs.")

    update_server_properties(instance_name, "resource-pack", rp_url)
    if rp_sha1:
        update_server_properties(instance_name, "resource-pack-sha1", rp_sha1)
    else:
        update_server_properties(instance_name, "resource-pack-sha1", "")

    print(f"Resource pack configuration for '{instance_name}' updated in server.properties.")
    return True


def attach_resourcepack(instance_name, resourcepack_value, resourcepack_port=None):
    config = read_config()
    instance_cfg = get_instance(instance_name)

    if not instance_cfg:
        print(f"Error: Instance '{instance_name}' does not exist. Please create it first.")
        return False

    is_url = resourcepack_value.startswith("http://") or resourcepack_value.startswith("https://")

    if not is_url:
        rp_path = Path(resourcepack_value)
        if not rp_path.is_file():
            print(f"Error: Resource pack file '{resourcepack_value}' not found.")
            return False
        resourcepack_value = str(rp_path.absolute())

    edit_instance(instance_name,
                  resourcepack=resourcepack_value,
                  resourcepack_port=resourcepack_port if resourcepack_port is not None else instance_cfg['resourcepack_port'])
    print("Resource pack information attached to '" + instance_name + "' successfully.")
    return True


def launch_server(instance_name):
    config = read_config()
    instance = get_instance(instance_name)
    if not instance:
        print(f"Instance '{instance_name}' not found.")
        return

    instance_path = Path(config['instances_folder']) / instance_name
    server_jar_path = instance_path / f"server.jar"

    try:
        if instance.get('auto_backup', False):
            print(f"Auto-backup enabled. Creating backup for '{instance_name}' before launch...")
            if not backup_instance(instance_name, "Auto-backup"):
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
        set_resourcepack(instance_name)

        java_exec = "java"

        memory_allocation = instance.get('memory', EMPTY_INSTANCE_CFG['memory'])

        command = [java_exec, f"-Xmx{memory_allocation}", f"-Xms{memory_allocation}", "-jar", str(server_jar_path)]
        needs_hosting = not (instance['resourcepack'].startswith("http://") or instance['resourcepack'].startswith("https://"))
        print(f"Launching server with command: {' '.join(command)}")
        print(needs_hosting)
        if needs_hosting:
            command.append("nogui")
            server_process = subprocess.run(command, cwd=instance_path)

            print(f"Server '{instance_name}' exited with code {server_process.returncode}.")
        else:
            process = subprocess.Popen(
                command,
                cwd=instance_path,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                stdin=subprocess.DEVNULL,
                start_new_session=True
            )

            def monitor_server():
                print(f"Server process started with PID {process.pid}. Monitoring...")
                process.wait()
                print(f"Server '{instance_name}' exited with code {process.returncode}.")

            threading.Thread(target=monitor_server, daemon=True).start()

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

def list_backups(instance_name):
    instance_cfg = get_instance(instance_name)
    backups = instance_cfg.get("backups", {})

    if not backups:
        print(f"\nNo backups found for instance '{instance_name}'.")
        return

    print(f"\n--- Backups for {instance_name} ---")
    print(f"{'ID':<10} {'Date/Time':<20} {'Version':<10} {'Description'}")
    print(f"{'-' * 10:<10} {'-' * 20:<20} {'-' * 10:<10} {'-' * 40}")

    for backup_id, info in backups.items():
        desc = info.get("desc", "")
        print(f"{backup_id:<10} {info.get('datetime', ''):<20} {info.get('version', ''):<10} {desc}")

    print("------------------------------------------------------------")


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
    parser.add_argument("-u","--upload", action="store_true", help="Upload the file to upload server.")
    parser.set_defaults(auto_backup=None)
    parser.add_argument("-rp", "--resourcepack",
                        help="Path or link to the resource pack .zip file to attach (for 'attach' and 'create' commands).")
    parser.add_argument("-rpp", "--resourcepack-port", type=int,
                        help="Port for the resource pack HTTP server (for 'attach', 'create', and 'edit' commands). Default is 2548.")
    parser.add_argument("-k", "--key",
                        help="Key for editing configs (not instances).")
    parser.add_argument("-v", "--value",
                        help="Value for editing configs (not instances).")
    parser.add_argument("-b", "--backup",
                        help="Backup options for some commands.")
    parser.add_argument("-c", "--command", required=True,
                        choices=["create", "launch", "check", "edit", "backup", "delete", "open", "attach", "list", "edit-config", "edit-sp", "rollback"],
                        help="Command to execute: 'create', 'launch', 'check', 'edit', 'backup', 'delete', 'open', 'attach', 'list', 'edit-config', 'edit-sp', 'rollback.")


    args = parser.parse_args()

    instance_commands = ["create", "launch", "check", "edit", "backup", "delete", "open", "attach", "edit-sp", "rollback"]
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
        resourcepack_path = args.resourcepack if args.resourcepack else ""
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
        if not any([args.version, args.memory, args.auto_backup is not None, args.resourcepack is not None, args.resourcepack_port is not None]):
            print("Error: At least one of version, memory, auto-backup, resource pack file, or resource pack port must be provided to edit an instance.")
            return
        try:
            edit_instance(args.instance, args.version, args.memory, args.auto_backup,
                          resourcepack=args.resourcepack, resourcepack_port=args.resourcepack_port)
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
        if args.backup:
            backup_instance(args.instance, desc=args.backup)
            return
        backup_instance(args.instance)

    elif args.command == "rollback":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist.")
            return
        if not args.backup:
            print(f"Specify backup if with '--backup' option.'")
            return
        rollback_instance(args.instance, args.backup)

    elif args.command == "delete":
        if not check_instance(args.instance):
            print(f"Error: Instance '{args.instance}' does not exist.")
            return
        confirm = input(f"Are you sure you want to delete instance '{args.instance}' and all its data? (Y/n): ").lower()
        if confirm == 'yes' or confirm == 'y' or confirm == '':
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
        if not args.resourcepack:
            print("Error: A resource pack file path (--resourcepack or -rp) is required for the 'attach' command.")
            return
        if args.upload:
            try:
                link = upload_file(args.resourcepack)
                print(link)
                attach_resourcepack(args.instance, link)
            except Exception as e:
                print(f"Failed to upload resourcepack '{args.resourcepack}': {e}. Trying to attach file.")
                attach_resourcepack(args.instance, args.resourcepack, args.resourcepack_port)
        else:
            attach_resourcepack(args.instance, args.resourcepack, args.resourcepack_port)

    elif args.command == "list":
        if args.instance:
            if not check_instance(args.instance):
                print(f"Error: Instance '{args.instance}' does not exist.")
                return
            list_backups(args.instance)
            return
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