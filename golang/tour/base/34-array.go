package main

import (
	"container/list"
	"fmt"
)

/*
数组
类型 [n]T 表示拥有 n 个 T 类型的值的数组.
表达式
var a [10]int
会将变量 a 声明为拥有 10 个整数的数组.

数组的长度是其类型的一部分, 因此数组不能改变大小.
这看起来是个限制, 不过没关系, Go 提供了更加便利的方法使用数组.
*/
func main() {

	var a [10]int
	a[0] = 1
	a[1] = 2
	fmt.Println(a)

	var strs [10]string
	strs[0] = "Hello, "
	strs[1] = "World!"
	fmt.Println(strs[0], strs[1])
	fmt.Println(strs)

	l := list.New()
	fmt.Println(l)
	l.PushBack(1)
	l.PushBack("xxx")
	fmt.Println(l)

	// 删除数组中最后一个元素
	arr3 := []int{1, 2, 3}
	fmt.Println(arr3[0 : len(arr3)-1])
}
