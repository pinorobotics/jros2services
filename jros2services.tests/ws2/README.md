Based on [ROS 2 Examples](https://github.com/ros2/examples) version 0.9.4

# Build

```bash
colcon build
. install/setup.zsh
```

# Setup

**jros2services** tests expect "build" and "install" folders to be placed under "out.<ROS_DISTRO>" folder.

``` bash
rm -rf out.$ROS_DISTRO
mkdir out.$ROS_DISTRO
cp -rf build out.$ROS_DISTRO
cp -rf install out.$ROS_DISTRO
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
