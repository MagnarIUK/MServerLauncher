import re
import textwrap

ANSI_ESCAPE = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~]|\].*?\x07|\].*?\x1b\\)')


def strip_ansi(text: str) -> str:
    return ANSI_ESCAPE.sub("", text)



class Table:
    def __init__(self, title: str, columns_data: list = None, width: int = 80):
        self.title = title
        self.columns_data = columns_data or []
        self._total_width = self._calculate_total_width()
        self.width = width


    def _calculate_total_width(self) -> int:
        if not self.columns_data:
            return 0

        total_columns_width = sum(width for _, width in self.columns_data)

        num_spaces = len(self.columns_data) - 1

        return total_columns_width + num_spaces

    def print_header(self):
        print("\n" + self.title.center(self._total_width) + "\n")

        header_parts = []
        separator_parts = []
        for name, width in self.columns_data:
            header_parts.append(f"{name:<{width}}")
            separator_parts.append(f"{'-' * width:<{width}}")

        print(" ".join(header_parts))
        print(" ".join(separator_parts))

    def print_row(self, row_values: list):
        if len(row_values) != len(self.columns_data):
            print(
                f"Error: Row values count ({len(row_values)}) does not match column count ({len(self.columns_data)}).")
            return

        content_parts = []
        for i, (col_name, width) in enumerate(self.columns_data):
            value = str(row_values[i])  # Ensure value is a string
            content_parts.append(f"{value:<{width}}")

        print(" ".join(content_parts))
    def print_row2(self, row_values: list):
        if len(row_values) != len(self.columns_data):
            print(
                f"Error: Row values count ({len(row_values)}) does not match column count ({len(self.columns_data)})."
            )
            return

        wrapped_cells = []
        for i, (col_name, width) in enumerate(self.columns_data):
            value = str(row_values[i])
            wrapped = textwrap.wrap(value, width=width) or [""]
            wrapped_cells.append(wrapped)

        max_lines = max(len(cell) for cell in wrapped_cells)

        for line_idx in range(max_lines):
            line_parts = []
            for col_idx, (col_name, width) in enumerate(self.columns_data):
                cell_lines = wrapped_cells[col_idx]
                if line_idx < len(cell_lines):
                    part = f"{cell_lines[line_idx]:<{width}}"
                else:
                    part = " " * width
                line_parts.append(part)
            print(" ".join(line_parts))
    def print_closing(self):
        separator_parts = []
        for _, width in self.columns_data:
            separator_parts.append(f"{'-' * width:<{width}}")
        print(" ".join(separator_parts))


    def print_vertical(self, data: dict, wrap_keys: int = 20, wrap_desc: bool = True):
        print("\n" + f"{self.title}".center(self.width))
        print("-" * self.width)

        for key, value in data.items():
            if isinstance(value, dict):
                value = value.get("username", "N/A")

            if isinstance(value, list):
                value = ", ".join(map(str, value))

            value = str(value)

            wrapped = textwrap.wrap(value, width=self.width - wrap_keys - 2) or [""]
            print(f"{key:<{wrap_keys}} {wrapped[0]}")
            for cont in wrapped[1:]:
                print(" " * wrap_keys + " " + cont)

        print("-" * self.width)