Based on [ROS 2 Examples](https://github.com/ros2/examples) version 0.9.4

# Build

```bash
colcon build
. install/setup.zsh
```

# Run

Client:
```bash
ros2 run examples_rclcpp_minimal_client client_main
```

Server:
```bash
ros2 run examples_rclcpp_minimal_service service_main 
```
