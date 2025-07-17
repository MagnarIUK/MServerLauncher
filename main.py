import hashlib
import sys
import shutil
import datetime

import requests
from pathlib import Path
import json
import os
import subprocess

EMPTY_CONFIG = {
    "instances_folder": str(Path.home().joinpath(".minecraft/server_instances").absolute())
}
EMPTY_INSTANCE_CFG = {
    "version": "",
    "name": "",
    "memory": "2048M",
    "auto_backup": False
}


def write_config(cfg):
    with open('config.json', 'w') as f:
        json.dump(cfg, f, indent=4)


def read_confid():
    if os.path.exists("config.json"):
        with open("config.json", "r") as cfg:
            return json.load(cfg)
    else:
        write_config(EMPTY_CONFIG)
        return EMPTY_CONFIG


def get_versions():
    response = requests.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    if response.status_code == 200:
        return response.json()
    else:
        return {"error": response.status_code, "description": response.text}


def get_version(ver_id):
    versions = get_versions().get('versions')
    version = None
    if ver_id == "latest" or ver_id == "l":
        ver_id = get_versions().get('latest').get("release")
    elif ver_id == "snapshot" or ver_id == "s":
        ver_id = get_versions().get('latest').get("snapshot")

    for entry in versions:
        if entry.get('id') == ver_id:
            print(entry.get('id'))
            version = entry
            break
    if version is None:
        return {"error": "404", "description": "Version not found"}

    response = requests.get(version.get('url'))
    if response.status_code == 200:
        return response.json()
    else:
        return {"error": response.status_code, "description": response.text}


def download_server(url, sha, destination):
    sha1 = hashlib.sha1()
    with requests.get(url, stream=True) as response:
        response.raise_for_status()
        with open(destination, "wb") as f:
            for chunk in response.iter_content(chunk_size=1024):
                if chunk:
                    f.write(chunk)
                    sha1.update(chunk)
    downloaded_sha = sha1.hexdigest()
    if downloaded_sha != sha:
        return {"error": "SHA mismatch", "expected": sha, "actual": downloaded_sha}
    print("Download complete and SHA verified.")
    return {"success": True, "sha": downloaded_sha}


def check_instance(name):
    config = read_confid()
    instances_path = Path(config['instances_folder'])
    instances_path.mkdir(parents=True, exist_ok=True)
    folders = [f.name for f in instances_path.iterdir() if f.is_dir() and f.joinpath("cfg.json").exists()]
    if name in folders:
        return True
    return False


def create_instance(name, version, memory, auto_backup=False):
    config = read_confid()
    instance_path = Path(config['instances_folder']).joinpath(name)
    instance_path.mkdir(parents=True, exist_ok=True)

    resolved_version = version
    if version == "latest" or version == "l":
        resolved_version = get_versions().get('latest').get("release")
    elif version == "snapshot" or version == "s":
        resolved_version = get_versions().get('latest').get("snapshot")

    with open(instance_path.joinpath("cfg.json"), "w") as cfg:
        conf = {
            "version": resolved_version,
            "name": name,
            "memory": memory,
            "auto_backup": auto_backup
        }
        json.dump(conf, cfg, indent=4)
    with open(instance_path.joinpath("eula.txt"), "w") as f:
        f.write("eula=true")


def edit_instance(name, version=None, memory=None, auto_backup=None):
    config = read_confid()
    instance_path = Path(config['instances_folder']).joinpath(name)
    with open(instance_path.joinpath("cfg.json"), "r") as f:
        conf = json.load(f)

    if version:
        resolved_version = version
        if version == "latest" or version == "l":
            resolved_version = get_versions().get('latest').get("release")
        elif version == "snapshot" or version == "s":
            resolved_version = get_versions().get('latest').get("snapshot")
        conf['version'] = resolved_version
    if memory:
        conf['memory'] = memory
    if auto_backup is not None:
        conf['auto_backup'] = auto_backup

    with open(instance_path.joinpath("cfg.json"), "w") as cfg:
        json.dump(conf, cfg, indent=4)


def get_instance(name):
    cfg = read_confid()
    instances_path = Path(cfg['instances_folder'])
    instance = instances_path / name
    config_path = instance / "cfg.json"
    if config_path.exists():
        with open(config_path, "r") as f:
            instance_cfg = json.load(f)
            if 'memory' not in instance_cfg:
                instance_cfg['memory'] = EMPTY_INSTANCE_CFG['memory']
            if 'auto_backup' not in instance_cfg:
                instance_cfg['auto_backup'] = EMPTY_INSTANCE_CFG['auto_backup']
            return instance_cfg
    return None


def backup_instance(instance_name):
    config = read_confid()
    instance_path = Path(config['instances_folder']).joinpath(instance_name)

    if not instance_path.is_dir():
        print(f"Error: Instance folder '{instance_name}' not found at '{instance_path}'.")
        return

    backups_path = instance_path / "backups"
    backups_path.mkdir(exist_ok=True)

    timestamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    world_folder = instance_path / "world"

    source_path = world_folder
    if not world_folder.is_dir():
        print(
            f"Warning: 'world' folder not found for instance '{instance_name}'. Backing up the entire instance directory.")
        source_path = instance_path

    backup_name = f"{instance_name}-world-backup-{timestamp}" if world_folder.is_dir() else f"{instance_name}-instance-backup-{timestamp}"
    destination_path = backups_path / backup_name

    try:
        shutil.make_archive(str(destination_path), 'zip', str(source_path))
        print(f"Backup created successfully: {destination_path}.zip")
        return True
    except Exception as e:
        print(f"Error creating backup for '{instance_name}': {e}")
        return False

def delete_instance(name):
    config = read_confid()
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

def launch_server(instance_name):
    config = read_confid()
    instance = get_instance(instance_name)
    if not instance:
        print(f"Instance '{instance_name}' not found.")
        return

    if instance.get('auto_backup', False):
        print(f"Auto-backup enabled. Creating backup for '{instance_name}' before launch...")
        if not backup_instance(instance_name):
            print("Auto-backup failed. Continuing with server launch anyway.")
        else:
            print("Auto-backup completed.")

    ver = get_version(instance.get('version'))
    if "error" in ver:
        print(f"Version error: {ver['description']}")
        return

    instance_path = Path(config['instances_folder']) / instance_name
    server_jar_url = ver.get('downloads', {}).get('server', {}).get('url')
    server_sha = ver.get('downloads', {}).get('server', {}).get('sha1')

    if not server_jar_url or not server_sha:
        print("Server download info missing.")
        return

    server_jar_path = instance_path / f"server.jar"

    current_sha1 = None
    if server_jar_path.exists():
        print("Checking existing server.jar SHA1...")
        hasher = hashlib.sha1()
        with open(server_jar_path, 'rb') as f:
            while chunk := f.read(4096):
                hasher.update(chunk)
        current_sha1 = hasher.hexdigest()

    if not server_jar_path.exists() or current_sha1 != server_sha:
        print(f"server.jar not found or SHA1 mismatch. Downloading {instance.get('version')} server.jar...")
        result = download_server(server_jar_url, server_sha, server_jar_path)
        if "error" in result:
            print(f"Download error: {result}")
            return
    else:
        print("server.jar is up to date.")

    java_exec = "java"
    if os.name == "nt":
        java_exec = "java.exe"

    memory_allocation = instance.get('memory', EMPTY_INSTANCE_CFG['memory'])

    try:
        command = [java_exec, f"-Xmx{memory_allocation}", f"-Xms{memory_allocation}", "-jar", str(server_jar_path),
                   "nogui"]
        print(f"Launching server with command: {' '.join(command)}")
        subprocess.run(command, cwd=instance_path)
    except Exception as e:
        print(f"Failed to launch server: {e}")


import argparse


def main():
    parser = argparse.ArgumentParser(description="Minecraft Server Launcher by MagnarIUK")
    parser.add_argument("-i", "--instance", required=True, help="Name of The Instance")
    parser.add_argument("-v", "--version", required=False, help="Instance Version")
    parser.add_argument("-m", "--memory", required=False,
                        help="Memory allocation for the server (e.g., 1024M, 2G). Default is 2048M.")
    parser.add_argument("-ab","--auto-backup", action="store_true",
                        help="Enable automatic world backup before launching the server.")
    parser.add_argument("-nab","--no-auto-backup", action="store_false", dest="auto_backup",
                        help="Disable automatic world backup before launching the server.")
    parser.set_defaults(auto_backup=None)
    parser.add_argument("-c", "--command", required=True, choices=["create", "launch", "check", "edit", "backup", "delete"],
                        help="Command to execute")

    args = parser.parse_args()

    if args.command == "check":
        exists = check_instance(args.instance)
        if exists:
            print(f"Instance '{args.instance}' exists.")
        else:
            print(f"Instance '{args.instance}' does not exist.")
    elif args.command == "create":
        ab = args.auto_backup if args.auto_backup is not None else False
        if not args.version:
            print("Error: Version is required to create an instance.")
            return
        if check_instance(args.instance):
            print(f"Instance '{args.instance}' already exists.")
            return
        memory_to_use = args.memory if args.memory else EMPTY_INSTANCE_CFG['memory']
        create_instance(args.instance, args.version, memory_to_use, ab)
        text = f"Instance '{args.instance}' created with version '{args.version}' and memory '{memory_to_use}'." if not ab else f"Instance '{args.instance}' created with version '{args.version}', memory '{memory_to_use}' and Auto Backup enabled."
        print(text)
    elif args.command == "edit":
        if not check_instance(args.instance):
            print(f"Instance '{args.instance}' does not exist.")
            return
        if not args.version and not args.memory and args.auto_backup is None:
            print("Error: Either version, memory, or auto-backup setting must be provided to edit an instance.")
            return
        edit_instance(args.instance, args.version, args.memory, args.auto_backup)
        print(f"Instance '{args.instance}' updated.")
    elif args.command == "launch":
        if not check_instance(args.instance):
            print(f"Instance '{args.instance}' does not exist.")
            return
        launch_server(args.instance)
    elif args.command == "backup":
        if not check_instance(args.instance):
            print(f"Instance '{args.instance}' does not exist.")
            return
        backup_instance(args.instance)
    elif args.command == "delete":
        if not check_instance(args.instance):
            print(f"Instance '{args.instance}' does not exist.")
            return
        delete_instance(args.instance)



if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nExecution interrupted by user. Exiting...")
        sys.exit(0)