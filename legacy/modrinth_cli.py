from typing import Optional

from modrinth_api_wrapper import Client
from table import Table

def main(command: list, instance: Optional[str] = None):
    client = Client()
    if len(command) == 0:
        print("No commands given.")
        return

    match command[0]:
        case "h" | "help":
            __help()
        case "s" | "search":
            __search(client, str.join(" ", command[1:]))
        case "getp" | "get_project":
            __get_project(client, command[1])
        case "getv" | "get_version":
            __get_version(client, command[1])
        case "lver" | "latest_version":
            __get_latest_version(client, command[1])
        case _:
            print(f"Unknown command: '{command}'")

def __help():
    help_table = Table(
        "Available Commands",
        [("Command", 20), ("Aliases", 20), ("Description", 50)]
    )
    help_table.print_header()
    help_table.print_row(["search <query>", "s <query>", "Searches for projects on Modrinth."])
    help_table.print_row(["getp <project_id>", "gp <project_id>", "Retrieves detailed info for a specific project."])
    help_table.print_row(["lv <project_id>", "lv <project_id>", "Retrieves info about the latest version of a project."])
    help_table.print_row(["getv <version_id>", "gv <version_id>", "Retrieves detailed info for a specific project."])
    help_table.print_row(["help", "h", "Displays this help message."])
    help_table.print_closing()

def __search(client: Client, query: str):
    print(f"Searching for '{query}'...")
    try:
        results = client.search_project(query)
        if not results:
            print("No projects found.")
            return

        table = Table(
            f"Search Results for '{query}'",
            [("ID", 12), ("Title", 30), ("Author", 20), ("Downloads", 10), ("Type", 20), ("Description", 50), ("Link", 35)]
        )

        table.print_header()
        for result in results.hits:
            table.print_row2([
                result.project_id,
                result.title,
                result.author,
                result.downloads,
                result.project_type,
                result.description,
                f"https://modrinth.com/mod/{result.project_id}"
            ])
        table.print_closing()

    except Exception as e:
        print(f"An error occurred during search: {e}")

def __get_project(client: Client, project_id: str):
    print(f"Fetching details for project ID '{project_id}'...")
    try:
        project_data = client.get_project(project_id)
        if not project_data:
            print(f"Project with ID '{project_id}' not found.")
            return

        table = Table(f"{project_data.title} info")
        table.print_vertical({
            "ID": project_data.id,
            "Title": project_data.title,
            "Type": project_data.project_type,
            "Downloads": project_data.downloads,
            "Versions": project_data.versions,
            "ServerSide": project_data.server_side,
            "Game Versions": project_data.game_versions,
            "Loaders": project_data.loaders,
            "Categories": project_data.categories,
            "Description": project_data.description,
            "URL": f"https://modrinth.com/mod/{project_data.id}"
        })
    except Exception as e:
        print(f"An error occurred while fetching project info: {e}")

def __get_version(client: Client, version_id: str):
    print(f"Fetching details for version ID '{version_id}'...")
    try:
        version_data = client.get_version(version_id)
        if not version_data:
            print(f"Version with ID '{version_data}' not found.")
            return
        project_data = client.get_project(version_data.project_id)
        table = Table(f"{project_data.title} {version_id} version info")
        table.print_vertical({
            "ID": version_data.id,
            "Name": version_data.name,
            "Version Number": version_data.version_number,
            "Game Versions": version_data.game_versions,
            "Loaders": version_data.loaders,
            "Version Type": version_data.version_type,
            "Published": version_data.date_published,
            "Downloads": version_data.downloads,
            "Changelog": version_data.changelog,
            "Changelog URL": version_data.changelog_url,
            "URL": f"https://modrinth.com/mod/{project_data.id}/version/{version_data.id}",
        })
    except Exception as e:
        print(f"An error occurred while fetching project info: {e}")

def __get_latest_version(client: Client, project_id: str):
    print(f"Fetching details for latest version of project '{project_id}'...")
    try:
        project_data = client.get_project(project_id)
        if not project_data:
            print(f"Project with ID '{project_id}' not found.")
            return
        version_data = client.get_version(project_data.versions[-1])
        table = Table(f"{project_data.title} latest version info")
        table.print_vertical({
            "ID": version_data.id,
            "Name": version_data.name,
            "Version Number": version_data.version_number,
            "Game Versions": version_data.game_versions,
            "Loaders": version_data.loaders,
            "Version Type": version_data.version_type,
            "Published": version_data.date_published,
            "Downloads": version_data.downloads,
            "Changelog": version_data.changelog,
            "Changelog URL": version_data.changelog_url,
            "URL": f"https://modrinth.com/mod/{project_data.id}/version/{version_data.id}"
        })
    except Exception as e:
        print(f"An error occurred while fetching project info: {e}")
