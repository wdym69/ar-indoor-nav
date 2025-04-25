from flask import Flask,request, jsonify
import cv2
import os
import numpy as np
from pathlib import Path
import shutil
import argparse
import shutil
from flask_cors import CORS  # Import CORS
import subprocess
from glob import glob
app = Flask(__name__)
CORS(app)

# finds the top k images from images dataset based on input image 
def retrieve_similar_images(query_image_path, database_folder, top_k=5, output_folder="topKImages"):
    # Initialize ORB detector (lightweight for retrieval)
    orb = cv2.ORB_create()

    # Ensure the output folder exists (clear if already exists)
    if os.path.exists(output_folder):
        shutil.rmtree(output_folder)  # Remove existing folder
    os.makedirs(output_folder)  # Create a new empty folder
    shutil.copy(query_image_path, os.path.join(output_folder, query_image_path))

    # Extract features for the query image
    query_img = cv2.imread(query_image_path, 0)
    kp_query, desc_query = orb.detectAndCompute(query_img, None)

    # Match against database images
    similarity_scores = []
    database_images = glob(f"{database_folder}/*.png")

    for db_image_path in database_images:
        db_img = cv2.imread(db_image_path, 0)
        kp_db, desc_db = orb.detectAndCompute(db_img, None)

        # Match features using Brute-Force
        bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
        matches = bf.match(desc_query, desc_db)
        similarity = len(matches)  # Simple score: number of matches

        similarity_scores.append((db_image_path, similarity))

    # Sort by similarity and select top-k images
    similarity_scores.sort(key=lambda x: x[1], reverse=True)
    top_k_images = [img[0] for img in similarity_scores[:top_k]]

    # Copy top-k images to the output folder
    for img_path in top_k_images:
        shutil.copy(img_path, os.path.join(output_folder, os.path.basename(img_path)))

    return top_k_images  # Return paths of stored images





# creates a txt file which will be pairs of top k images with input image in this database folder = topKImages folder
def generate_pairs_file(query_image, database_folder, output_file = 'image_pairs.txt'):
    """
    Generate a pairs file for SuperGlue matching
    Args:
        query_image: Name of the query image (e.g., 'out32.png')
        database_folder: Folder containing all database images
        output_file: Where to save the pairs file
    """
    # Get all images in the database folder
    database_images = [f for f in os.listdir(database_folder) 
                      if f.lower().endswith('.png')]
    
    # Create pairs with the query image
    with open(output_file, 'w') as f:
        for db_image in database_images:
            # Skip if the database image is the query image
            if query_image != db_image:
                # Write relative paths from the SuperGlue root directory
                query_path = os.path.join(query_image)
                db_path = os.path.join(db_image)
                f.write(f'{query_path} {db_path}\n')

    print(f'Generated pairs file with {len(database_images)-1} pairs')
    print(f'Pairs file saved to: {output_file}')






#finds the best matching image based on our input image from the list of top k images 
def find_best_match():
    dump_path = Path('dump_match_pairs')
    best_match_file = None
    max_matches = -1
    best_match_confidence = 0
    
    for npz_file in dump_path.glob('*_matches.npz'):
        try:
            data = np.load(npz_file)
            matches = data['matches']
            match_confidence = data['match_confidence']
            
            valid_matches = (matches > -1).sum()
            valid_confidence = match_confidence[matches > -1].mean() if valid_matches > 0 else 0
            
            if valid_matches > max_matches:
                max_matches = valid_matches
                best_match_file = npz_file
                best_match_confidence = valid_confidence
                
        except Exception as e:
            print(f"Error processing {npz_file}: {e}")
            continue
    
    if best_match_file is None:
        return None, 0, 0
    
    match_image = best_match_file.stem.replace('_matches', '')
    extracted_name = match_image.split('_')[-1]
    return extracted_name, max_matches, best_match_confidence









#finds the output image id based on colmap images 
def find_image_id(image_name, images_file="images.txt"):
    with open(images_file, "r") as file:
        for line in file:
            parts = line.strip().split()
            if len(parts) > 1 and parts[-1] == image_name:
                return int(parts[0])  # IMAGE_ID is the first column
    return None  # Return None if image not found

# Function to extract quaternion and translation vector for a specific image ID
def get_image_pose(image_id, images_file="images.txt"):
    with open(images_file, "r") as file:
        for line in file:
            # skip comments and empty lines
            if line.startswith('#') or not line.strip():
                continue

            parts = line.strip().split()
            # try to interpret the first token as an integer ID;
            # if that fails, this is the 2D-point line, so skip it
            try:
                current_id = int(parts[0])
            except ValueError:
                continue

            # now we know this is the header line
            if current_id == image_id and len(parts) >= 8:
                qw, qx, qy, qz = map(float, parts[1:5])
                tx, ty, tz      = map(float, parts[5:8])
                return qw, qx, qy, qz, tx, ty, tz

    return None  # Image ID not found


# Function to convert quaternion to rotation matrix
def quaternion_to_rotation_matrix(qw, qx, qy, qz):
    """Convert quaternion to rotation matrix"""
    # Normalize the quaternion
    norm = np.sqrt(qw*qw + qx*qx + qy*qy + qz*qz)
    qw /= norm
    qx /= norm
    qy /= norm
    qz /= norm
    
    # Calculate rotation matrix elements
    r11 = 1 - 2*qy*qy - 2*qz*qz
    r12 = 2*qx*qy - 2*qz*qw
    r13 = 2*qx*qz + 2*qy*qw
    
    r21 = 2*qx*qy + 2*qz*qw
    r22 = 1 - 2*qx*qx - 2*qz*qz
    r23 = 2*qy*qz - 2*qx*qw
    
    r31 = 2*qx*qz - 2*qy*qw
    r32 = 2*qy*qz + 2*qx*qw
    r33 = 1 - 2*qx*qx - 2*qy*qy
    
    rotation_matrix = np.array([
        [r11, r12, r13],
        [r21, r22, r23],
        [r31, r32, r33]
    ])
    
    return rotation_matrix

# Function to create transformation matrix from rotation matrix and translation vector
def create_transformation_matrix(rotation_matrix, tx, ty, tz):
    """Create 4x4 transformation matrix from rotation matrix and translation vector"""
    transformation_matrix = np.eye(4)  # Start with identity matrix
    transformation_matrix[:3, :3] = rotation_matrix  # Set rotation part
    transformation_matrix[:3, 3] = [tx, ty, tz]  # Set translation part
    
    return transformation_matrix.tolist()  # Return as list for JSON serialization






#gives user coordinate by taking matched image id from database and points3D.txt

def parse_points3D_file(file_path):
    """
    Parse the points3D.txt file and return a dictionary mapping point3D_ID to its data
    """
    points3D_dict = {}
    with open(file_path, 'r') as f:
        for line in f:
            # Skip comments and empty lines
            if line.startswith('#') or not line.strip():
                continue
            
            # Split the line into components
            elements = line.strip().split()
            if not elements:
                continue
                
            point3D_id = int(elements[0])
            x, y, z = map(float, elements[1:4])
            # Track information starts from element 8
            track_info = elements[8:]
            
            # Parse track information into (image_id, point2D_idx) pairs
            tracks = []
            for i in range(0, len(track_info), 2):
                if i + 1 < len(track_info):
                    image_id = int(track_info[i])
                    point2D_idx = int(track_info[i + 1])
                    tracks.append((image_id, point2D_idx))
            
            points3D_dict[point3D_id] = {
                'coordinates': np.array([x, y, z]),
                'tracks': tracks
            }
            
    return points3D_dict

def get_3D_coordinates_for_image(points3D_dict, target_image_id):
    """
    Get all 3D coordinates associated with a specific image ID
    """
    coordinates = []
    
    # Iterate through all 3D points
    for point3D_id, point_data in points3D_dict.items():
        # Check if the image_id appears in the track information
        for image_id, _ in point_data['tracks']:
            if image_id == target_image_id:
                coordinates.append(point_data['coordinates'])
                break
    
    return np.array(coordinates)

def calculate_centroid(coordinates):
    """
    Calculate the centroid of a set of 3D coordinates
    """
    if len(coordinates) == 0:
        return None
    return np.mean(coordinates, axis=0)

def get_image_position(points3D_file_path, target_image_id):
    """
    Main function to get the estimated position for a given image ID
    """
    # Parse the points3D file
    points3D_dict = parse_points3D_file(points3D_file_path)
    
    # Get all 3D coordinates associated with the image
    coordinates = get_3D_coordinates_for_image(points3D_dict, target_image_id)
    
    # Calculate and return the centroid
    if len(coordinates) == 0:
        return None, 0
    
    centroid = calculate_centroid(coordinates)
    return centroid, len(coordinates)


















#this code gives us the way point from start position to goal position
from heapq import heappush, heappop
from dataclasses import dataclass, field
from typing import List, Set, Dict, Tuple

@dataclass
class Node:
    x: int
    y: int
    z: int
    g_cost: float = float('inf')
    h_cost: float = 0
    parent: 'Node' = None
    
    @property
    def f_cost(self) -> float:
        return self.g_cost + self.h_cost
    
    def __lt__(self, other):
        return self.f_cost < other.f_cost
    
    def __eq__(self, other):
        if not isinstance(other, Node):
            return False
        return self.x == other.x and self.y == other.y and self.z == other.z
    
    def __hash__(self):
        return hash((self.x, self.y, self.z))

class Grid3D:
    def __init__(self, size: int, cell_size: float = 1.0):
        """
        Initialize 3D grid for pathfinding
        size: number of cells in each dimension
        cell_size: size of each cell in world units
        """
        self.size = size
        self.cell_size = cell_size
        self.grid = np.zeros((size, size, size), dtype=bool)  # False = walkable, True = obstacle
        
    def world_to_grid(self, x: float, y: float, z: float) -> Tuple[int, int, int]:
        """Convert world coordinates to grid coordinates"""
        gx = int(np.clip(x / self.cell_size + self.size // 2, 0, self.size - 1))
        gy = int(np.clip(y / self.cell_size + self.size // 2, 0, self.size - 1))
        gz = int(np.clip(z / self.cell_size + self.size // 2, 0, self.size - 1))
        return gx, gy, gz
    
    def grid_to_world(self, gx: int, gy: int, gz: int) -> Tuple[float, float, float]:
        """Convert grid coordinates to world coordinates"""
        x = (gx - self.size // 2) * self.cell_size
        y = (gy - self.size // 2) * self.cell_size
        z = (gz - self.size // 2) * self.cell_size
        return x, y, z
    
    def is_walkable(self, x: int, y: int, z: int) -> bool:
        """Check if a grid cell is walkable"""
        if 0 <= x < self.size and 0 <= y < self.size and 0 <= z < self.size:
            return not self.grid[x, y, z]
        return False
    
    def set_obstacle(self, x: int, y: int, z: int):
        """Set a cell as an obstacle"""
        self.grid[x, y, z] = True

class PathFinder:
    def __init__(self, grid: Grid3D):
        self.grid = grid
        
    def get_neighbors(self, node: Node) -> List[Node]:
        """Get walkable neighboring nodes"""
        neighbors = []
        # Check all 26 neighbors (including diagonals)
        for dx in [-1, 0, 1]:
            for dy in [-1, 0, 1]:
                for dz in [-1, 0, 1]:
                    if dx == 0 and dy == 0 and dz == 0:
                        continue
                    
                    new_x = node.x + dx
                    new_y = node.y + dy
                    new_z = node.z + dz
                    
                    if self.grid.is_walkable(new_x, new_y, new_z):
                        # Calculate movement cost (diagonal movements cost more)
                        cost = np.sqrt(dx*dx + dy*dy + dz*dz)
                        neighbors.append((Node(new_x, new_y, new_z), cost))
        
        return neighbors
    
    def heuristic(self, node: Node, goal: Node) -> float:
        """Calculate heuristic (Euclidean distance)"""
        return np.sqrt((node.x - goal.x)**2 + (node.y - goal.y)**2 + (node.z - goal.z)**2)
    
    def find_path(self, start_pos: Tuple[float, float, float], 
                  goal_pos: Tuple[float, float, float]) -> List[Tuple[float, float, float]]:
        """Find path between two points in world coordinates"""
        # Convert world coordinates to grid coordinates
        start_grid = self.grid.world_to_grid(*start_pos)
        goal_grid = self.grid.world_to_grid(*goal_pos)
        
        # Create start and goal nodes
        start = Node(*start_grid)
        goal = Node(*goal_grid)
        
        # Initialize start node
        start.g_cost = 0
        start.h_cost = self.heuristic(start, goal)
        
        # Initialize open and closed sets
        open_set: List[Node] = [start]
        closed_set: Set[Node] = set()
        
        while open_set:
            current = heappop(open_set)
            
            if current == goal:
                # Reconstruct path
                path = []
                while current:
                    world_pos = self.grid.grid_to_world(current.x, current.y, current.z)
                    path.append(world_pos)
                    current = current.parent
                return list(reversed(path))
            
            closed_set.add(current)
            
            # Check all neighbors
            for neighbor, cost in self.get_neighbors(current):
                if neighbor in closed_set:
                    continue
                
                tentative_g_cost = current.g_cost + cost
                
                if neighbor not in open_set:
                    heappush(open_set, neighbor)
                elif tentative_g_cost >= neighbor.g_cost:
                    continue
                
                neighbor.parent = current
                neighbor.g_cost = tentative_g_cost
                neighbor.h_cost = self.heuristic(neighbor, goal)
        
        return []  # No path found

# Add the path to the response to be sent back to frontend
def main(start_pos, goal_pos):
    # Create a 50x50x50 grid with 0.5 unit cell size
    grid = Grid3D(size=50, cell_size=0.5)
    
    # Create pathfinder
    pathfinder = PathFinder(grid)
    
    # Find path
    path = pathfinder.find_path(start_pos, goal_pos)
    
    waypoints = []
    if path:
        print("Path found! Waypoints:")
        for i, point in enumerate(path):
            print(f"Waypoint {i}: ({point[0]:.2f}, {point[1]:.2f}, {point[2]:.2f})")
            waypoints.append({
                "waypoint_id": i,
                "x": round(point[0], 3),
                "y": round(point[1], 3),
                "z": round(point[2], 3)
            })
    else:
        print("No path found!")
        waypoints = []  # No path found, return an empty list

    return waypoints



















@app.route('/upload', methods=['POST'])
def upload_image():
    print(request.files)
    if 'image' not in request.files:
        return jsonify({"error": "No image file provided"}), 400
    
    image = request.files['image']

    try:
        dest_x = float(request.form['dest_x'])
        dest_y = float(request.form['dest_y'])
        dest_z = float(request.form['dest_z'])
    except (KeyError, ValueError):
        return jsonify({"error": "Invalid or missing dest_x/dest_y/dest_z"}), 400
    

    if image.filename == '':
        return jsonify({"error": "No selected file"}), 400

    # Save the image in the current directory
    image_path = os.path.join(os.getcwd(), image.filename)
    image.save(image_path)

    # Retrieve similar images
    list_of_k_images = retrieve_similar_images(image.filename, 'images')
    print(list_of_k_images)

    # Generate pairs file
    generate_pairs_file(image.filename, 'topKImages', 'image_pairs.txt')
    folder_path = "dump_match_pairs"
    if os.path.exists(folder_path):
        shutil.rmtree(folder_path)
        print(f"Folder {folder_path} has been deleted successfully.")
    else:
        print(f"The folder {folder_path} does not exist.")

    # Run match pairs
    command = [
        "python", "match_pairs.py",
        "--input_pairs", "image_pairs.txt",
        "--input_dir", "topKImages/",
        "--output_dir", "dump_match_pairs/"
    ]
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    print(result.stdout)

    # Find best matching image
    best_image, num_matches, confidence = find_best_match()
    best_image += ".png"
    print("Best image found:", best_image)

    # Get image ID
    image_id = find_image_id(best_image)
    if image_id is not None:
        print(f"Image ID for {image_id}")
    else:
        print(f"Image not found in images.txt")

    # Extract quaternion and translation values for the image
    transformation_matrix = None
    if image_id is not None:
        # Get quaternion and translation values
        pose_data = get_image_pose(image_id)
        if pose_data:
            qw, qx, qy, qz, tx, ty, tz = pose_data
            print(f"Quaternion: {qw}, {qx}, {qy}, {qz}")
            print(f"Translation: {tx}, {ty}, {tz}")
            
            # Convert quaternion to rotation matrix
            rotation_matrix = quaternion_to_rotation_matrix(qw, qx, qy, qz)
            
            # Create transformation matrix
            transformation_matrix = create_transformation_matrix(rotation_matrix, tx, ty, tz)
            print("Transformation matrix created")

    points3D_file_path = "points3D.txt"
    target_image_id = int(image_id)
    
    position, num_points = get_image_position(points3D_file_path, target_image_id)
    
    if position is not None:
        print(f"Estimated position for image {target_image_id}:")
        print(f"X: {position[0]:.3f}")
        print(f"Y: {position[1]:.3f}")
        print(f"Z: {position[2]:.3f}")
        print(f"Calculated using {num_points} 3D points")
    else:
        print(f"No 3D points found for image {target_image_id}")

    start_pos = (round(position[0], 3), round(position[1], 3), round(position[2], 3))
    goal_pos = (dest_x, dest_y, dest_z) 
       
    waypoints = main(start_pos, goal_pos)

    # Send the waypoints and transformation matrix back to the frontend as a JSON response
    return jsonify({
        "message": "Image uploaded successfully", 
        "waypoints": waypoints,
        "transformation_matrix": transformation_matrix
    })


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)